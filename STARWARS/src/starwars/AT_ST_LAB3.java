package starwars;

import agents.BB1F;
import agents.DEST;
import agents.LARVAFirstAgent;
import agents.MTT;
import ai.Choice;
import ai.DecisionSet;
import jade.core.AID;
import jade.lang.acl.ACLMessage;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class AT_ST_LAB3 extends AT_ST_LAB2 {

    // boleano para el caso de que seamos host de la sesion y tengamo que hacer el open problem
    boolean host = true;

    //n es el numero de equipos participando en la sesion
    int n = 1;

    public void setup() {
        this.enableDeepLARVAMonitoring();
        //LARVAFirstAgent.defSessionAlias("Grupo304");
        super.setup();
        showPerceptions = true;
        this.deactivateSequenceDiagrams();

        logger.onEcho();
        logger.onTabular();
        myStatus = Status.START;
        this.setupEnvironment();

        A = new DecisionSet();
        A.addChoice(new Choice("MOVE")).
                addChoice(new Choice("LEFT")).
                addChoice(new Choice("RIGHT"));
    }


    @Override
    public void Execute() {
        Info("Status: " + myStatus.name());
        switch (myStatus) {
            case START:
                myStatus = Status.CHECKIN;
                break;
            case CHECKIN:
                // The execution of a state (as a method) returns
                // the next state
                myStatus = MyCheckin();
                break;
            case OPENPROBLEM:
                if (host) {
                    myStatus = MyOpenProblem();
                } else {
                    myStatus = Status.JOINSESSION;
                }
                break;
            case JOINSESSION:
                myStatus = MyJoinSession();
                break;
            case SOLVEPROBLEM:
                myStatus = MySolveProblem();
                break;
            case CLOSEPROBLEM:
                if (host) {
                    myStatus = MyCloseProblem();
                } else {
                    myStatus = Status.CHECKOUT;
                }
                break;
            case CHECKOUT:
                myStatus = MyCheckout();
                break;
            case EXIT:
            default:
                doExit();
                break;
        }
    }


    @Override
    public AT_ST_FULL.Status MyOpenProblem() {

        if (this.DFGetAllProvidersOf(service).isEmpty()) {
            Error("Service PMANAGER is down");
            return AT_ST_FULL.Status.CHECKOUT;
        }
        problemManager = this.DFGetAllProvidersOf(service).get(0);
        Info("Found problem manager " + problemManager);
        // Seleccionar el problema, en este caso son los APR, NOT, SOB, MAT
        // de la práctica
        problem = this.inputSelect("Please select problem to solve", problems, problem);
        if (problem == null) {
            return AT_ST_FULL.Status.CHECKOUT;
        }
        this.outbox = new ACLMessage();
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(problemManager, AID.ISLOCALNAME));

        // Cambio añadido
        outbox.setContent("Request open " + problem + " alias " + sessionAlias);
        // Cambio performativa
        outbox.setPerformative(ACLMessage.REQUEST);

        this.LARVAsend(outbox);
        Info("opening problem " + problem + " to " + problemManager);
        open = LARVAblockingReceive();
        Info(problemManager + " says: " + open.getContent());
        content = open.getContent();
        contentTokens = content.split(" ");
        if (contentTokens[0].toUpperCase().equals("AGREE")) {
            sessionKey = contentTokens[4];
            session = LARVAblockingReceive();
            sessionManager = session.getSender().getLocalName();
            Info(sessionManager + " says: " + session.getContent());
            return AT_ST_FULL.Status.JOINSESSION;
        } else {
            Error(content);
            return AT_ST_FULL.Status.CHECKOUT;
        }
    }

    // Se incluyen Performativas y lanzamiento de NPCs necesarios
    @Override
    public AT_ST_FULL.Status MyJoinSession(){
        outbox = session.createReply();

        // Pedir las ciudades disponibles
        outbox.setContent("Query cities session " + sessionKey);

        // Cambio performativa y conversational id
        outbox.setPerformative(ACLMessage.QUERY_REF);
        outbox.setConversationId(sessionKey);

        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        this.getEnvironment().setExternalPerceptions(session.getContent());

        // Guardamos la lista de ciudades proporcionada
        cities = getEnvironment().getCityList();

        // Preguntamos al usuario en qué ciudad quiere aparecer
        current_city = this.inputSelect("Please select city to start", cities, "");
        city_coord = getEnvironment().getCityPosition(current_city);

        this.resetAutoNAV();
        this.DFAddMyServices(new String[]{"TYPE ITT"});
        outbox = session.createReply();

        // Iniciamos sesión en las coordenadas de la ciudad seleccionada
        outbox.setContent("Request join session " + sessionKey + " at " + city_coord.getXInt() + " " + city_coord.getYInt());

        // Cambio performativa y conversational id
        outbox.setPerformative(ACLMessage.REQUEST);
        outbox.setConversationId(sessionKey);

        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();

        if (!session.getContent().startsWith("Confirm")){
            Error("Could not join session " + sessionKey + " due " + session.getContent());
            return AT_ST_FULL.Status.CLOSEPROBLEM;
        }

        // Ejecutar los NPCs
        this.doPrepareNPC(1, DEST.class);
        this.doPrepareNPC((int) Math.ceil(n / 2.0), BB1F.class);//n es el numero de veces que se lanza
        //this.doPrepareNPC(0, YV.class);
        //Se añade para casos de prueba en lo que solo hay un equipo
        var nMTT = n == 1 ? 2 : n;
        this.doPrepareNPC(nMTT-1, MTT.class);

        outbox = session.createReply();

        // Obtenemos las misiones del problema abierto.
        // IMPORTANTE: tiene que coincidir con el problema abierto
        outbox.setContent("Query missions session " + sessionKey);

        // Cambio performativa y conversational id
        outbox.setPerformative(ACLMessage.QUERY_REF);
        outbox.setConversationId(sessionKey);
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        getEnvironment().setExternalPerceptions(session.getContent());

        // Antes de seleccionar la misión es necesario actualizar las percepciones
        this.MyReadPerceptions();
        return SelectMission();
    }

    // Recarga energía del agente ITT.
    public void EnergyRecharge() {
        int numMessage = 0;

        // Seleccionamos los BB1F de nuestra session y los guardamos en sessionProviders
        Info("Recharging... (asking BB1F)");
        ArrayList<String> globalProviders = this.DFGetAllProvidersOf("TYPE BB1F");
        ArrayList<String> sessionProviders = new ArrayList<String>();
        for(var globalProvider : globalProviders) {
            if (this.DFHasService(globalProvider, sessionKey)) {
                sessionProviders.add(globalProvider);
            }
        }
        ArrayList<Integer> distances = new ArrayList<Integer>();

        // Ordenamos los BB1F por cercanía
        // NOTA: Queda comentado hasta que Luis cambie el numero máximo de TRANSPONDER

        /*for(var provider : providers){
            outbox = new ACLMessage();
            outbox.setSender(getAID());
            outbox.addReceiver(new AID(provider, AID.ISLOCALNAME));
            outbox.setProtocol("DROIDSHIP");
            outbox.setPerformative(ACLMessage.QUERY_REF);
            outbox.setReplyWith(String.valueOf(numMessage));
            numMessage ++;
            outbox.setContent("TRANSPONDER");
            this.LARVAsend(outbox);
            session = LARVAblockingReceive();
            long time = 500;
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e){};
            contentTokens = session.getContent().split("/");
            String coordinates = contentTokens[4];
            coordinates = coordinates.replace("GPS ", "");

            var location = new Point3D(coordinates);
            distances.add(E.getGPS().gridDistanceTo(location));
        }

        Map<Integer, String> map = new HashMap<Integer, String>();
        for(Integer i = 0; i < providers.size(); i++) {
          map.put(distances.get(i),providers.get(i));
        }

        Collections.sort(distances);
        providers.clear();

        for(Integer i = 0; i < map.size(); i++) {
          providers.add(map.get(distances.get(i)));
        }*/

        // Solicitamos recarga a todos los BB1F (por cercanía) hasta que
        // recibimos un AGREE por parte de alguno. Luego le enviamos nuestras
        // coordenadas
        boolean rechargeAgentFound = false;
        while (!rechargeAgentFound){
            for(String provider: sessionProviders){
                outbox = new ACLMessage();
                outbox.setSender(getAID());
                outbox.addReceiver(new AID(provider, AID.ISLOCALNAME));
                outbox.setPerformative(ACLMessage.REQUEST);
                outbox.setProtocol("DROIDSHIP");
                outbox.setConversationId(sessionKey);
                outbox.setContent("REFILL");
                outbox.setReplyWith(String.valueOf(numMessage));
                numMessage ++;
                this.LARVAsend(outbox);

                bb1f = LARVAblockingReceive();
                if (bb1f.getPerformative()==ACLMessage.AGREE){
                    rechargeAgentFound = true;
                    break;
                }else if (bb1f.getPerformative()==ACLMessage.REFUSE){
                    bb1f = LARVAblockingReceive();
                }
            }
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e){};
        }

        // Esperamos hasta que llegue el BB1F y nos recargue. Cuando nos recarga,
        // salimos del bucle de espera (while) y leemos percepciones. La recarga
        // ha sido realizada correctamente
        boolean chargeDone = false;
        while(!chargeDone){
            bb1f = LARVAblockingReceive();
            if(bb1f.getPerformative() == ACLMessage.INFORM)
                chargeDone = true;
        }

        this.MyReadPerceptions();
        Info("Rechage completed");
    }

}


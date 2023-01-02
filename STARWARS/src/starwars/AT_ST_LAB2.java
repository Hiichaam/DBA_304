package starwars;

import Environment.Environment;
import agents.BB1F;
import agents.DEST;
import agents.MTT;
import ai.Choice;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import java.util.ArrayList;

/**
 *
 * @author dcardenas11
 */
public class AT_ST_LAB2 extends AT_ST_LAB1{

    String destCity = ""; // Ciudad en la que se encuentra el DESTROYER. Se
                          // averigua en String getMoveByCity(String droidship)
    String destProvider = ""; // Nombre del DESTROYER de nuestra session. Se
                          // averigua en String getMoveByCity(String droidship)
    String[] peopleNames; // Array de los nombres de los Jedis de una ciudad. Se
                          // averigua en String[] queryPeopleName(String type)
    String[] peopleNamesToTransfer; // Numero de Jedis que se van a transferir. Se
    int EnergyLimitToAskRecharge = 15; // Umbral a partir del cual solicitamos recarga
    ACLMessage mtt, bb1f, dest; // Mensajes para cada NPC con el que se interactua
    
    
    // Cambio en isTargetBack. Si el objetivo está atrás que gire para un lado
    // Necesario en Hon1 -> desde la primera ciudad (el DEST puede encontrarse
    // justo detrás)
    
    /**
     * 
     * @author Hicham Bouchemma 
     * @author David Correa
     */
    @Override
    protected double U(Environment E, Choice a){
        if (whichWall.equals("LEFT")) {
            return goFollowWallLeft(E, a);
        } else if(whichWall.equals("RIGHT")){
            return goFollowWallRight(E, a);
        } else if(E.isTargetBack()){
            if (a.getName().equals("LEFT")) {
                return Choice.ANY_VALUE;
            }
            return Choice.MAX_UTILITY;
        } else if (!E.isFreeFront()) {
            return goAvoid(E, a);
        } else {
            return goAhead(E, a);
        }
    }
    
    
    // Se añaden performativas de Lab2
    
    /**
     * 
     * @author Alex Herrera
     */
    @Override
    public boolean MyReadPerceptions (){
        Info("Reading perceptions...");
        outbox = session.createReply();
        outbox.setContent("Query sensors session " + sessionKey);

        outbox.setConversationId(sessionKey);
        outbox.setPerformative(ACLMessage.QUERY_REF);
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();

        if (session.getContent().startsWith("Failure")){
            Error("Unable to read perceptions due to " + session.getContent());
            return false;
        }        
        this.getEnvironment().setExternalPerceptions(session.getContent());
        //Info(this.easyPrintPerceptions());
        return true;
    }

    
    // Recarga energía del agente ITT. 
    
    /**
     * 
     * @author Hicham Bouchemma
     * @author Dani Cardenas
     * @author David Correa
     * @author Alex Herrera
     */
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

    
    // Funcion de captura de Jedis. Se encarga de contactar al agente de 
    // BackUp MTT, de conseguir los nombres de los Jedis y de capturarlos
    
    /**
     *
     * @author Hicham Bouchemma
     * @author Dani Cardenas 
     */

    protected AT_ST_FULL.Status doCapture(String  nCaptures, String type){
        int i = 0;
        int numMessage = 0;
        int numCaptures = Integer.parseInt(nCaptures);
        Info("Capturing " + nCaptures + " people " + type);
        
        ArrayList<String> globalProviders = this.DFGetAllProvidersOf("TYPE MTT");
        ArrayList<String> sessionProviders = new ArrayList<String>();
        for(var provider : globalProviders) {
            if (this.DFHasService(provider, sessionKey)) {
                sessionProviders.add(provider);
            }
        }
        ArrayList<Integer> distances = new ArrayList<Integer>();

        //Esto es para ordenar los providers por cercania
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
        for( i = 0; i < providers.size(); i++) {
            map.put(distances.get(i),providers.get(i));
        }

        Collections.sort(distances);
        providers.clear();

        for( i = 0; i < map.size(); i++) {
            providers.add(map.get(distances.get(i)));
        }*/
        
        boolean mttFound = false;
        while (!mttFound){
            for(String provider: sessionProviders){
                outbox = new ACLMessage();
                outbox.setSender(getAID());
                outbox.addReceiver(new AID(provider, AID.ISLOCALNAME));
                outbox.setPerformative(ACLMessage.REQUEST);
                outbox.setProtocol("DROIDSHIP");
                outbox.setConversationId(sessionKey);
                outbox.setContent("BACKUP");
                outbox.setReplyWith(String.valueOf(numMessage));
                numMessage ++;
                this.LARVAsend(outbox);

                mtt = LARVAblockingReceive();
                if (mtt.getPerformative()==ACLMessage.AGREE){
                    mttFound = true;
                    break;
                }else if (mtt.getPerformative()==ACLMessage.REFUSE){
                        mtt = LARVAblockingReceive();
                }
            }
        }

        boolean backupHasArrived = false;
        while(!backupHasArrived){
            mtt = LARVAblockingReceive();
            if(mtt.getPerformative() == ACLMessage.INFORM)
                backupHasArrived = true; 
        }
        
        // Hasta este punto la función es calcada a void EnergyRecharge().
        // Ahora preguntamos al SM por los nombres de los Jedis y los vamos 
        // capturando uno a uno, hasta que hemos llegado al número de capturas
        // solicitadas

        peopleNames = queryPeopleNames(type);
        while (i < numCaptures){
            outbox = session.createReply();
            outbox.setContent("Request capture " + peopleNames[i] + " session " + sessionKey);
            outbox.setPerformative(ACLMessage.REQUEST);
            outbox.setConversationId(sessionKey);
            this.LARVAsend(outbox);
            session = LARVAblockingReceive();
            
            if(session.getPerformative() == ACLMessage.INFORM)
                ++i;
        }
        
        // Liberamos al MTT usado con un mensaje de Cancel. En este punto ya 
        // tenemos los Jedis solicitados en nuestra bodega
        
        outbox = mtt.createReply();
        outbox.setPerformative(ACLMessage.CANCEL);
        outbox.setContent("");
        outbox.setProtocol("DROIDSHIP");
        outbox.setConversationId(sessionKey);
        this.LARVAsend(outbox);
        return myStatus;
    }
    
    // Función similar al antiguo doQueryPeople(String type) salvo que en lugar
    // de calcular el número de personas de tipo type, obtiene sus nombres para 
    // posteriormente poder capturarlos
    // NOTA: En este Lab2, type = Jedi
    /**
     * 
     * @author Dani Cardenas
     */
    protected String[] queryPeopleNames(String type){
        Info("Querying people " + type);
        outbox = session.createReply();
        outbox.setContent("Query " + type.toUpperCase() + " session " + sessionKey);
        outbox.setPerformative(ACLMessage.QUERY_REF);
        outbox.setConversationId(sessionKey);
        this.LARVAsend(outbox);
        session = LARVAblockingReceive();
        getEnvironment().setExternalPerceptions(session.getContent());
        Info("Found " + getEnvironment().getPeople().length + " " + type + " in " + getEnvironment().getCurrentCity());
        return this.getEnvironment().getPeople();
    }
    
    
    // Función que localiza los DESTROYER de nuestra session, selecciona uno
    // de ellos y consigue la ciudad en la que se encuentra.
    // Es parte del procedimiento para resolver el Goal MOVEBY DEST
    /**
     * 
     * @author Dani Cardenas 
     * @author Hicham Bouchemma
     */
    public String getMoveByCity(String droidship){
        Info("Getting DEST name and position ...");
        
        // Obtenemos el nombre de el/los DEST de nuestra session (lo de siempre vaya)
        ArrayList<String> destProviders = this.DFGetAllProvidersOf("TYPE " + droidship.toUpperCase());
        for(String provider: destProviders){
            if(this.DFHasService(provider, sessionKey)){
                destProvider = provider;
            }
        }
        
        // Solicitamos un TRANSPONDER para saber en qué ciudad se encuentra. Para 
        // ello, usamos un mensaje con performativa QUERY_REF.
        outbox = new ACLMessage();
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(destProvider, AID.ISLOCALNAME));
        outbox.setPerformative(ACLMessage.QUERY_REF);
        outbox.setProtocol("DROIDSHIP");
        outbox.setConversationId(sessionKey);
        outbox.setContent("TRANSPONDER");
        this.LARVAsend(outbox);
        dest = LARVAblockingReceive();  
      
        // Obtenemos la ciudad gracias al TRANSPONDER recibido y solicitamos
        // al SM el camino hasta allí. Cuando lo tenemos, lo incluimos en nuestro
        // Environment para poder ir hacia él. Acabamos devolviendo la ciudad
        // para posteriormente poder saber que hemos llegado satisfactoriamente
        contentTokens = dest.getContent().split("/")[3].split(" ");
        destCity = contentTokens[2];
        
        outbox = session.createReply();
        outbox.setContent("Request course in " + destCity + " session " + sessionKey);
        outbox.setPerformative(ACLMessage.REQUEST);
        outbox.setConversationId(sessionKey);
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        getEnvironment().setExternalPerceptions(session.getContent());
        Info("DEST found");
        return destCity;
    }
    
    
    // Función que transfiere las capturas al DEST una vez hemos llegado
    // a la ciudad donde se encuentra
    /**
     * 
     * @author Dani Cardenas 
     * @author Hicham Bouchemma
     * 
     */
    public Status doTransfer(){
        int peopleToTransfer = peopleNames.length;
        Info("We are Transfering ...\n" );
        
        // Al primer if únicamente se accede al transferir el primer Jedi
        // (en principio) y sirve para configurar el mensaje. Después, vamos
        // transfiriendo los Jedis uno a uno cambiando el contenido del 
        // mensaje en función del nombre del Jedi correspondiente
        while(peopleToTransfer > 0){
            if (dest == null) {
                outbox = new ACLMessage();
                outbox.setSender(getAID());
                outbox.addReceiver(new AID(destProvider, AID.ISLOCALNAME));
            } else { // Else follow the dialogue
                outbox = dest.createReply();
            } 
               
            outbox.setPerformative(ACLMessage.REQUEST);
            outbox.setContent("TRANSFER " + peopleNames[peopleToTransfer-1]);
            outbox.setProtocol("DROIDSHIP");
            outbox.setConversationId(sessionKey);
            outbox.setReplyWith(peopleNames[peopleToTransfer-1]);
            this.LARVAsend(outbox);
            dest = LARVAblockingReceive();

            Info("================\n" + dest.getContent());
            if (dest.getPerformative()==ACLMessage.INFORM){
                    Info("Jedi named [" + peopleNames[peopleToTransfer-1] + "] has been transfered");
                    peopleToTransfer -= 1;
            }
        }
        
        return myStatus;
    }
    
    
    // Se incluyen Performativas
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
        this.doPrepareNPC(4, BB1F.class);//n es el numero de veces que se lanza
        //this.doPrepareNPC(0, YV.class);
        this.doPrepareNPC(3, MTT.class);

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
    
    
    // Se incluyen Performativas
    @Override
    public Status myAssistedNavigation(int goalx, int goaly) {
        Info("Requesting course to " + goalx + " " + goaly);
        outbox = session.createReply();
        
        outbox.setContent("Request course to " + goalx + " " + goaly + " Session " + sessionKey);
        
        outbox.setConversationId(sessionKey);
        outbox.setPerformative(ACLMessage.REQUEST);
        
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        getEnvironment().setExternalPerceptions(session.getContent());
        return Status.CHECKIN.SOLVEPROBLEM;
    }
    
    
    // Se incluyen Performativas
    @Override
    public boolean MyExecuteAction (String action){
        Info("Executing action " + action);
        outbox = session.createReply();
        outbox.setContent("Request execute " + action + " session " + sessionKey);
        
        outbox.setConversationId(sessionKey);
        outbox.setPerformative(ACLMessage.REQUEST);
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        
        if (!session.getContent().startsWith("Inform")){
            Error("Unable to execute action " + action + " due to " + session.getContent());
            return false;
        }
        return true;
    }

    // Se incluyen Performativas y la opción de resolver las nuevas 
    // misiones de Lab2: CAPTURE, MOVEBY y TRANSFER 
    /**
     * 
     * @author Dani Cardenas
     * @author Alex Herrera
     */
    @Override
    public AT_ST_FULL.Status MySolveProblem(){
        if (getEnvironment().getCurrentMission().isOver()){
            Info("The problem is over");
            Message("The problem " + problem + " has been solved");
            return AT_ST_FULL.Status.CLOSEPROBLEM;
        }
        
        // Obtenemos el objetivo actual. Los objetivos van separados por espacios
        // por lo que los splitteamos por espacios
        current_goal = getEnvironment().getCurrentGoal().split(" ");
  
        // Si el objetivo es movernos, utilizamos en asistente de LARVA
        // para desplazarnos y se realiza el algoritmo de movimiento
        if (current_goal[0].equals("MOVEIN")){
            if (!getEnvironment().getCurrentCity().equals(current_goal[1])){
                outbox = session.createReply();
                outbox.setContent("Request course in " + current_goal[1] + " session " + sessionKey);
                
                // Cambio performativa y conversational id
                outbox.setPerformative(ACLMessage.REQUEST);
                outbox.setConversationId(sessionKey);
                
                this.LARVAsend(outbox);
                session = this.LARVAblockingReceive();
                getEnvironment().setExternalPerceptions(session.getContent());
                return MovementGoal("");
            } else {
                Info("Goal " + current_goal[0] + " " + current_goal[1] + " has been solved!");
                //getEnvironment().getCurrentMission().nextGoal();
                getEnvironment().setNextGoal();
                return Status.SOLVEPROBLEM;
            }  
        // Si el objetivo es listar, obtenemos la lista de personas dado un tipo
        // y se avanza al siguiente objetivo
        } else if (current_goal[0].equals("LIST")){
            doQueryPeople(current_goal[1]);
            Info("Goal " + current_goal[0] + " " + current_goal[1] + " " + current_goal[2] + " has been solved!");
            getEnvironment().setNextGoal();
            return Status.SOLVEPROBLEM;
        } else if (current_goal[0].equals("REPORT")){
            doReportTypeGoal(current_goal[1]);
            Info("Goal " + current_goal[0] + " " + current_goal[1] + " has been solved!");
            getEnvironment().setNextGoal();
            return Status.SOLVEPROBLEM;
        } else if (current_goal[0].equals("CAPTURE")){
            doCapture(current_goal[1], current_goal[2]);
            Info("Goal " + this.getEnvironment().getCurrentGoal() + " has been solved!");
            getEnvironment().setNextGoal();
            return Status.SOLVEPROBLEM;
        } else if (current_goal[0].equals("MOVEBY")){
            if (destCity.equals("")){
                destCity = getMoveByCity(current_goal[1]);
                return MovementGoal(destCity);
            } else {
                Info("Goal " + current_goal[0] + " " + current_goal[1] + " has been solved!");
                getEnvironment().setNextGoal();
                return Status.SOLVEPROBLEM;
            }
        } else if (current_goal[0].equals("TRANSFER")){
            Info("Estoy en el transfer");
            System.out.println(getEnvironment().getCurrentGoal());
            doTransfer();
            
            Info("Goal " + this.getEnvironment().getCurrentGoal() + " has been solved!");
            getEnvironment().setNextGoal();
            return Status.SOLVEPROBLEM;            
        }
        
        return Status.EXIT;
    }    
    
    
    // Se incluyen Performativas
    @Override
    public Status MyCloseProblem(){
        this.doDestroyNPC();
        
        // AFter all, it is mandatory closing the problem
        // by replying to the backup message
        Info("Plan = " + preplan);
        outbox = open.createReply();
        
        outbox.setContent("Cancel session " + sessionKey);
        outbox.setConversationId(sessionKey);
        outbox.setPerformative(ACLMessage.CANCEL);
        
        Info("Closing problem Helloworld, session " + sessionKey);
        this.LARVAsend(outbox);
        inbox = LARVAblockingReceive();
        Info(problemManager + " says: " + inbox.getContent());
        return Status.CHECKOUT;
    }
    
    
    // Se actualiza como se indica en el guión de prácticas: incluyendo TRANSPONDER
    @Override
    public ACLMessage LARVAblockingReceive() {
        boolean exit = false;
        ACLMessage res = null;
        while (!exit) {
            res = super.LARVAblockingReceive();
            if(res.getContent().equals("TRANSPONDER") && res.getPerformative() == ACLMessage.QUERY_REF) {
                outbox = res.createReply();
                outbox.setPerformative(ACLMessage.INFORM);
                outbox.setContent(this.Transponder());
                LARVAsend(outbox);
            } else{
                exit = true;
            }
        }
        return res;
    }

    
    // Se incluye un parámetro String city que será vacío (city = "") cuando
    // el Goal sea MOVEIN y que almacenará la ciudad donde se encuentra el
    // DEST cuando el Goal sea MOVEBY.
    // Para verificar si hemos llegado a la ciudad, verificamos si hemos llegado
    // a la ciudad dependiendo del Goal:
    // 1) current_goal[1]: Para Goals del tipo MOVEIN
    // 2) String city: Para Goals del tipo MOVEBY
    // El resto de la función se mantiene igual que la versión del Lab1
    /**
     * 
     * @author Dani Cardenas 
     */
    public AT_ST_FULL.Status MovementGoal(String city){
        behaviour = AgPlan(E, A);
        // Si se ha completado el plan se avanza de objetivo
        if (behaviour == null || behaviour.isEmpty()) {
            if ("MOVEIN".equals(current_goal[0])){
                if (getEnvironment().getCurrentCity().equals(current_goal[1])){
                    Info("Goal " + current_goal[0] + " " + current_goal[1] + " has been solved!");
                    getEnvironment().setNextGoal();
                    return AT_ST_FULL.Status.SOLVEPROBLEM;
                }
	    } else { // "MOVEBY <droidship>".equals(current_goal)
	    	if (getEnvironment().getCurrentCity().equals(city)){
                    Info("Goal " + current_goal[0] + " " + current_goal[1] + " has been solved!");
                    getEnvironment().setNextGoal();
                    return AT_ST_FULL.Status.SOLVEPROBLEM;
                }
	    }
            Alert("Found no plan to execute");
            return AT_ST_FULL.Status.CLOSEPROBLEM;
        // Si hay acciones por realizar del plan
        } else {// Execute
            Info("Found plan: " + behaviour.toString());
            while (!behaviour.isEmpty()) {
                a = behaviour.get(0);
                behaviour.remove(0);
                Info("Excuting " + a);
                this.MyExecuteAction(a.getName());
                if(getEnvironment().getEnergy() < EnergyLimitToAskRecharge){
                    EnergyRecharge();
                }
                if (!Ve(E)) {
                    this.Error("The agent is not alive: " + E.getStatus());
                    return AT_ST_FULL.Status.CLOSEPROBLEM;
                }
            }
            this.MyReadPerceptions();
            return MovementGoal(city);   // función iterativa
        }
    }

}
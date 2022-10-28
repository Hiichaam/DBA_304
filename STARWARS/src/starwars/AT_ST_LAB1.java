/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package starwars;

import Environment.Environment;
import agents.BB1F;
import agents.DEST;
import agents.MTT;
import agents.YV;
import geometry.Point3D;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import java.util.ArrayList;
/**
 *
 * @author dcardenas11
 */
public class AT_ST_LAB1 extends AT_ST_FULL{
    String[] cities,
             current_goal;
    String current_city;
    Point3D city_coord;
    Map<String, String> reportMap = new HashMap<String, String>();
    
    @Override
    public Status MyOpenProblem() {

        if (this.DFGetAllProvidersOf(service).isEmpty()) {
            Error("Service PMANAGER is down");
            return Status.CHECKOUT;
        }
        problemManager = this.DFGetAllProvidersOf(service).get(0);
        Info("Found problem manager " + problemManager);
        // Selector of the problem to solve
        problem = this.inputSelect("Please select problem to solve", problems, problem);
        if (problem == null) {
            return Status.CHECKOUT;
        }
        this.outbox = new ACLMessage();
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(problemManager, AID.ISLOCALNAME));
        outbox.setContent("Request open " + problem + " alias " + sessionAlias);
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
            return Status.JOINSESSION;
        } else {
            Error(content);
            return Status.CHECKOUT;
        }
    }
    
    @Override
    public Status MyJoinSession(){
        outbox = session.createReply();
        outbox.setContent("Query cities session " + sessionKey);
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        this.getEnvironment().setExternalPerceptions(session.getContent());
        cities = getEnvironment().getCityList();
        current_city = this.inputSelect("Please select city to start", cities, "");
        city_coord = getEnvironment().getCityPosition(current_city);
        
        this.resetAutoNAV();
        this.DFAddMyServices(new String[]{"TYPE ITT"});
        outbox = session.createReply();
        outbox.setContent("Request join session " + sessionKey + " at " + city_coord.getXInt() + " " + city_coord.getYInt());
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        
        if (!session.getContent().startsWith("Confirm")){
            Error("Could not join session " + sessionKey + " due " + session.getContent());
            return Status.CLOSEPROBLEM;
        }
        
        this.doPrepareNPC(1, DEST.class);
        //this.doPrepareNPC(2, BB1F.class);
        //this.doPrepareNPC(0, YV.class);
        //this.doPrepareNPC(0, MTT.class);

        outbox = session.createReply();
        outbox.setContent("Query missions session " + sessionKey);
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        getEnvironment().setExternalPerceptions(session.getContent());
        
        this.MyReadPerceptions();
        return SelectMission();
    }
    
    @Override
    protected String chooseMission(){
        Info("Choosing a mission");
        String m = "";
        if (getEnvironment().getAllMissions().length == 1){
            m = getEnvironment().getAllMissions()[0];
        } else {
            m = this.inputSelect("Please choose a mission",getEnvironment().getAllMissions(),"");
        }
        Info("Selected mission " + m);
        return m;
    }
    
    public Status SelectMission(){
        String m = chooseMission();
        if (m == null){
            return Status.CLOSEPROBLEM;
        }
        getEnvironment().setCurrentMission(m);
        return Status.SOLVEPROBLEM;
    }
    
    protected Status doQueryPeople(String type){
        Info("Querying people " + type);
        outbox = session.createReply();
        outbox.setContent("Query " + type.toUpperCase() + " session " + sessionKey);
        this.LARVAsend(outbox);
        session = LARVAblockingReceive();
        getEnvironment().setExternalPerceptions(session.getContent());
        Message("Found " + getEnvironment().getPeople().length + " " + type + " in " + getEnvironment().getCurrentCity());
        if(reportMap.containsKey(getEnvironment().getCurrentCity())){
            String tmp = reportMap.get(getEnvironment().getCurrentCity());
            tmp += " " + type + " " + getEnvironment().getPeople().length;
            reportMap.put(getEnvironment().getCurrentCity(), tmp);
        } else {
            reportMap.put(getEnvironment().getCurrentCity(), new String(type + " " + getEnvironment().getPeople().length));
        }
        return myStatus;
    }
    
    @Override
    public Status MySolveProblem(){
        if (getEnvironment().getCurrentMission().isOver()){
            Info("The problem is over");
            Message("The problem " + problem + " has been solved");
            return Status.CLOSEPROBLEM;
        }
        
        current_goal = getEnvironment().getCurrentGoal().split(" ");
        if (current_goal[0].equals("MOVEIN")){
            outbox = session.createReply();
            outbox.setContent("Request course in " + current_goal[1] + " session " + sessionKey);
            this.LARVAsend(outbox);
            session = this.LARVAblockingReceive();
            getEnvironment().setExternalPerceptions(session.getContent());
            return MovementGoal();  
        } else if (current_goal[0].equals("LIST")){
            doQueryPeople(current_goal[1]);
            Info("Goal " + current_goal[0] + " " + current_goal[1] + " " + current_goal[2] + " has been solved!");
            getEnvironment().getCurrentMission().nextGoal();
            return Status.SOLVEPROBLEM;
        } else {
            doReportTypeGoal(current_goal[1]);
            Info("Goal " + current_goal[0] + " " + current_goal[1] + " has been solved!");
            getEnvironment().getCurrentMission().nextGoal();
            return Status.SOLVEPROBLEM;
        }
    }
    
    protected Status doReportTypeGoal(String type){
        String Report = "REPORT;";
        Iterator it = reportMap.keySet().iterator();
        
        boolean first = true;
        for (var entry : reportMap.entrySet()) {
            if(first){
                Report += entry.getKey() + " " + entry.getValue();
                first = false;
            } else
                Report += ";" + entry.getKey() + " " + entry.getValue();
        }
        Report += ";";

        //Info("REPORT:" + Report);
        //this.getEnvironment().
        Info("Reporting " + type);
        DroidShip.Debug();
        ArrayList<String> providers = this.DFGetAllProvidersOf("TYPE " + type.toUpperCase());
        
        String receiverAgent = "";
        for(String provider: providers){
            if(this.DFHasService(provider, sessionKey)){
                receiverAgent = provider;
            }
        }
        //ArrayList<String> services = this.DFGetServiceList();
        //ArrayList<String> providers = this.DFGetProviderList();
        
        
        Info("Agent type " + type + ": " + receiverAgent + " found!");
        outbox = new ACLMessage();
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(receiverAgent, AID.ISLOCALNAME));
        outbox.setPerformative(ACLMessage.INFORM_REF);
        outbox.setProtocol("DROIDSHIP");
        outbox.setContent(Report);
        this.LARVAsend(outbox);
        session = LARVAblockingReceive();
        
        if (session.getContent().split(" ")[0].toUpperCase().equals("CONFIRM")){
            Info("Agent " + receiverAgent + " has sent " + session.getContent());
            getEnvironment().getCurrentMission().nextGoal();
            return Status.SOLVEPROBLEM;
        } else {
            Error(content);
            return Status.CHECKOUT;
        }
    }
    
    public Status MovementGoal(){
        // if (G(E)) {
        //    Info("The problem is over");
        //    this.Message("The problem " + problem + " has been solved");
        //    return Status.CLOSEPROBLEM;
        //}
        behaviour = AgPlan(E, A);
        if (behaviour == null || behaviour.isEmpty()) {
            if (getEnvironment().getCurrentCity().equals(current_goal[1])){
                Info("Goal " + current_goal[0] + " " + current_goal[1] + " has been solved!");
                getEnvironment().getCurrentMission().nextGoal();
                return Status.SOLVEPROBLEM;
            }
            Alert("Found no plan to execute");
            return Status.CLOSEPROBLEM;
        } else {// Execute
            Info("Found plan: " + behaviour.toString());
            while (!behaviour.isEmpty()) {
                a = behaviour.get(0);
                behaviour.remove(0);
                Info("Excuting " + a);
                this.MyExecuteAction(a.getName());
                if (!Ve(E)) {
                    this.Error("The agent is not alive: " + E.getStatus());
                    return Status.CLOSEPROBLEM;
                }
            }
            this.MyReadPerceptions();
            return MovementGoal();
        }
    }
    
    @Override
    public Status MyCloseProblem(){
        this.doDestroyNPC();
        return super.MyCloseProblem();
    }
    
    @Override
    public double goAvoid(Environment E, Choice a){
        if (E.isTargetLeft()){
            if (a.getName().equals("LEFT")) {
            nextWhichwall = "RIGHT";
            nextdistance = E.getDistance();
            nextPoint = E.getGPS();
            return Choice.ANY_VALUE;
            } 
        }else{
           if (a.getName().equals("RIGHT")) {
            nextWhichwall = "LEFT";
            nextdistance = E.getDistance();
            nextPoint = E.getGPS();
            return Choice.ANY_VALUE;
            }   
        }
        
        return Choice.MAX_UTILITY;
    }
}

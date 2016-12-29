import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.util.HashMap;

import java.util.Map;


public class BuyerAgent extends Agent {

    private static int REQUEST_REPEAT_TIME = 120000;
    private Map<String, Integer> productList = new HashMap<String, Integer>();
    private Map<String, Integer> busket = new HashMap<String, Integer>();

    private AID[] sellerAgents;

    String localName = "buyer";
    AID id = new AID(localName, AID.ISLOCALNAME);
    @Override

    protected void setup(){
        System.out.println("Hello! Buyer-agent " + getAID().getName() + " is ready.");
        Object [] args = getArguments();
        if (args != null && args.length > 0){
            productList = (Map<String, Integer>) args[0];
            System.out.println("Trying to fill the basket with the list of products:" + productList);
            addBehaviour(new TickerBehaviour(this, REQUEST_REPEAT_TIME) {
                @Override
                protected void onTick() {
                    DFAgentDescription dfAgentDescription = new DFAgentDescription();
                    ServiceDescription serviceDescription = new ServiceDescription();
                    serviceDescription.setType("product-selling");
                    dfAgentDescription.addServices(serviceDescription);
                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, dfAgentDescription);
                        sellerAgents = new AID[result.length];
                        for (int i = 0; i < result.length; i++){
                            sellerAgents[i] = result[i].getName();
                        }
                    }
                    catch (FIPAException fIPAException){
                        fIPAException.printStackTrace();
                    }
                    myAgent.addBehaviour(new RequestPerformer());
                }
            });
        }
        else {
            System.out.println("There isn't any products in your list");
            doDelete();
        }
    }
    @Override
    protected void takeDown(){
        System.out.println("Buyer agent" + getAID().getName() + "was deleted");
    }
    private class RequestPerformer extends Behaviour{
        private AID[] bestItemSellers;
        private AID[] bestItemPrices;
        private int repliesCount;
        private MessageTemplate messageTemplate;
        private int step = 0;

        public  void action(){
            switch (step) {
                case 0:
                    ACLMessage aclMessage = new ACLMessage(ACLMessage.CFP);
                    for (int i = 0; i < sellerAgents.length; i++) {
                        aclMessage.addReceiver(sellerAgents[i]);
                    }
                    aclMessage.setContent(productList.toString());
                    aclMessage.setConversationId("product-selling");
                    aclMessage.setReplyWith("aclMessage" + System.currentTimeMillis());
                    myAgent.send(aclMessage);

                    messageTemplate = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
                            MessageTemplate.MatchInReplyTo(aclMessage.getReplyWith()));
                    step = 1;
                    break;
                case 1:
                    ACLMessage reply = myAgent.receive(messageTemplate);
                    if (reply != null) {
                        if (reply.getPerformative() == ACLMessage.PROPOSE) {
                            int[] itemPrices;
                            for (int i = 0; i < bestItemSellers.length; i++) {
                                itemPrices[i] = Integer.parseInt(reply.getContent());
                                if (bestItemSellers[i] == null || itemPrices[i] < bestItemPrices[i]) {
                                    bestItemPrices[i] = itemPrices[i];
                                    bestItemSellers[i] = reply.getSender();
                                }
                            }
                        }
                        repliesCount++;
                        if (repliesCount++ >= sellerAgents.length) {
                            step = 2;
                        }
                    } else {
                        block();
                    }
                    break;
                case 2:
                    ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    for (int i = 0; i < bestItemSellers.length; i++) {
                        order.addReceiver(bestItemSellers[i]);
                        order.setContent(productList.get(i));
                        order.setConversationId("product-selling");
                        order.setReplyWith("order" + System.currentTimeMillis());
                        myAgent.send(order);
                        messageTemplate = MessageTemplate.and(MessageTemplate.MatchConversationId("product-selling"),
                                MessageTemplate.MatchInReplyTo(order.getReplyWith()));
                    }
                    step = 3;
                    break;
                case 3:
                    reply = myAgent.receive(mt);
                    if (reply != null){
                        for (int i = 0; i < bestItemSellers.length; i++) {
                            if (reply.getPerformative() == ACLMessage.INFORM) {
                                System.out.println(productList.get(i) + " successfully purchased from agent " + reply.getSender().getName());
                                System.out.println("itemPrice = " + bestItemPrices[i]);
                                myAgent.doDelete();
                            } else {
                                System.out.println("Attempt failed: requested book already sold.");
                            }
                        }
                    }
                    else {
                        block();
                    }
                    step = 4;
                    break;
            }
        }
        public boolean done() {
            for (int i = 0; i < bestItemSellers.length; i++) {
                if (step == 2 && bestItemSellers[i]==null){
                    System.out.println("Attempt failed: " + productList.get(i) + " not available for sale");
                }
                return ((step == 2 && bestItemSellers[i] == null) || step == 4);
            }
        }
    }
}
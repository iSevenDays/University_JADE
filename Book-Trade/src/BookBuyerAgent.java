import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

public class BookBuyerAgent extends Agent {
	// название книги, которую необходимо приобрести агенту
	private String targetBookTitle;
	// список агентов продавцов
	private AID[] sellerAgents;
	public static int CICLE_TIME = 60000;

	// инициализация агента-покупател
	protected void setup() {
		// сообщенеи об готовности агента-покупателя
		System.out.println("Hallo! Buyer-agent "+getAID().getName()+" is ready.");

		// получение название книги, которую необходимо приобрести агенту, как начального аргумента
		Object[] args = getArguments();
		if (args != null && args.length > 0) {
			targetBookTitle = (String) args[0];
			System.out.println("Target book is "+targetBookTitle);

			// добавляем TickerBehaviour, целью которого является планирование запросов агента-покупателя агентам-продавцам
			addBehaviour(new TickerBehaviour(this, CICLE_TIME) {
				protected void onTick() {
					System.out.println("Trying to buy "+targetBookTitle);
					// обновления списка агентов-продавцов
					DFAgentDescription template = new DFAgentDescription();
					ServiceDescription sd = new ServiceDescription();
					sd.setType("book-selling");
					template.addServices(sd);
					try {
						DFAgentDescription[] result = DFService.search(myAgent, template); 
						System.out.println("Found the following seller agents:");
						sellerAgents = new AID[result.length];
						for (int i = 0; i < result.length; ++i) {
							sellerAgents[i] = result[i].getName();
							System.out.println(sellerAgents[i].getName());
						}
					}
					catch (FIPAException fe) {
						fe.printStackTrace();
					}

					// выполняем запрос
					myAgent.addBehaviour(new RequestPerformer());
				}
			} );
		}
		else {
			// перкращаем работу агента
			System.out.println("No target book title specified");
			doDelete();
		}
	}
	protected void takeDown() {
		System.out.println("Buyer-agent "+getAID().getName()+" terminating.");
	}

	//описываем класс поведения агента
	private class RequestPerformer extends Behaviour {
		private AID bestSeller; // агент-продавец, предложивший книгу по лучше	 цене
		private int bestPrice;  // лучшая предложеная цена
		private int repliesCnt = 0; // счетчик ответов от агентов-продавцов
		private MessageTemplate mt; // шаблон приема ответов от агентов-продавцов
		private int step = 0;

		public void action() {
			switch (step) {
			case 0:
				// отправляем запросі ко всем агентам-продавцам
				ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
				for (int i = 0; i < sellerAgents.length; ++i) {
					cfp.addReceiver(sellerAgents[i]);
				} 
				cfp.setContent(targetBookTitle);
				cfp.setConversationId("book-trade");
				cfp.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
				myAgent.send(cfp);
				// подготавливаем шаблон для получения предложений
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
						MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
				step = 1;
				break;
			case 1:
				// принимаем все предложения/отказі от агентов-продавцов
				ACLMessage reply = myAgent.receive(mt);
				if (reply != null) {
					// ответ принят
					if (reply.getPerformative() == ACLMessage.PROPOSE) {
						// предложение
						int price = Integer.parseInt(reply.getContent());
						if (bestSeller == null || price < bestPrice) {
							// лучшее на данный момент предложение
							bestPrice = price;
							bestSeller = reply.getSender();
						}
					}
					repliesCnt++;
					if (repliesCnt >= sellerAgents.length) {
						// приняли все ответы
						step = 2; 
					}
				}
				else {
					block();
				}
				break;
			case 2:
				// отправляем запрос агенту-продавцу, предложившему лучшую цену
				ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
				order.addReceiver(bestSeller);
				order.setContent(targetBookTitle);
				order.setConversationId("book-trade");
				order.setReplyWith("order"+System.currentTimeMillis());
				myAgent.send(order);
				// подготавливаем шаблон на получение ответа по запросу на покупку
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
						MessageTemplate.MatchInReplyTo(order.getReplyWith()));
				step = 3;
				break;
			case 3:      
				// принимаем ответ на запрос на покупку
				reply = myAgent.receive(mt);
				if (reply != null) {
					// ответ принят
					if (reply.getPerformative() == ACLMessage.INFORM) {
						// агент достиг своей цели, завершаем его работу
						System.out.println(targetBookTitle+" successfully purchased from agent "+reply.getSender().getName());
						System.out.println("Price = "+bestPrice);
						myAgent.doDelete();
					}
					else {
						System.out.println("Attempt failed: requested book already sold.");
					}

					step = 4;
				}
				else {
					block();
				}
				break;
			}        
		}

		public boolean done() {
			if (step == 2 && bestSeller == null) {
				System.out.println("Attempt failed: "+targetBookTitle+" not available for sale");
			}
			return ((step == 2 && bestSeller == null) || step == 4);
		}
	}
}

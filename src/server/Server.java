package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import client.Client;

public class Server implements AutoCloseable {

	private ServerSocket socket;
	private ExecutorService executor;
	public List<Channel> channelList = new ArrayList<>();
	private final static String[] forbiddenWords = { "retele", "cts", "restanta", "licenta" };

	public Server(int port) throws IOException {
		socket = new ServerSocket(port);
		Channel channel = new Channel("BT-SPORT",
				"BT sport is the home of 52 Premier League games a season, the UEFA Champions League, Premiership Rugby and much, much more",
				null);
		channelList.add(channel);
		executor = Executors.newFixedThreadPool(20 * Runtime.getRuntime().availableProcessors());
		executor.execute(() -> {
			while (!socket.isClosed()) {
				try {
					Client client = new Client(socket.accept());
					sendChannelList(client);
					executor.submit(() -> {
						while (!client.isClosed()) {
							try {
								String command = client.receive();

								String[] words = command.split(" ");
								if (words.length == 2) {
									if (command.equals("list channels")) {
										sendChannelList(client);
									} else {
										switch (words[0]) {
										case "broadcast":
											broadcastMessage(words[1]);

											break;
										case "publish":
											channelList.add(new Channel(words[1], null, client));
											client.send("\r\n");
											notify("publish", words[1], client);
											break;
										case "delete":
											deleteChannel(words[1], client);
											break;
										case "subscribe":
											subscribeToChannel(words[1], client);
											break;
										case "unsubscribe":
											unsubscribeFromChannel(words[1], client);
											break;
										default:
											client.send(
													"\r\nInvalid command. Type /help to list all available commands\r\n");
											break;
										}
									}
								} else if (words.length >= 3) {
									int descriptionStart = words[0].length() + words[1].length() + 2;
									String channelDescription = command.substring(descriptionStart);
									switch (words[0]) {
									case "publish":
										channelList.add(new Channel(words[1], channelDescription, client));
										client.send("\r\n");
										notify("publish", words[1], client);
										break;
									case "news":
										sendNewsMessage(words[1], command.substring(descriptionStart), client);
										client.send("\r\n");
										break;
									default:
										client.send(
												"\r\nInvalid command. Type /help to list all available commands\r\n");
										break;
									}

								} else {
									switch (command) {
									case "/help":
										sendHelp(client);
										break;
									case "close":
										handleClose(client);
										break;
									default:
										client.send(
												"\r\nInvalid command. Type /help to list all available commands\r\n");
										break;
									}
								}
							} catch (Exception e) {
							}
						}
						try {
							client.close();
						} catch (Exception e) {
						}
					});
				} catch (Exception e) {
				}
			}
		});
	}

	private void subscribeToChannel(String channelName, Client client) {
		boolean channelExists = false;
		for (Channel channel : channelList) {
			if (channel.getName().equals(channelName)) {
				channelExists = true;
				if (channel.getSubscribers().contains(client)) {
					client.send("\r\nAlready subscribed to this channel.\r\n");
				} else {
					channel.getSubscribers().add(client);
					client.send("\r\nSuccessfully subscribed to channel " + channelName + "\r\n");
				}
			}
		}

		if (!channelExists)
			client.send("\r\nThere is no channel named " + channelName + "\r\n");

	}

	private void unsubscribeFromChannel(String channelName, Client client) {
		boolean channelExists = false;
		for (Channel channel : channelList) {
			if (channel.getName().equals(channelName)) {
				channelExists = true;
				if (channel.getSubscribers().contains(client)) {
					channel.getSubscribers().remove(client);
					client.send("\r\nSuccessfully unsubscribed from channel " + channelName + "\r\n");
				} else {
					client.send("\r\nYou are not subscribed to this channel.\r\n");
				}
			}
		}

		if (!channelExists)
			client.send("\r\nThere is no channel named " + channelName + "\r\n");

	}

	private void deleteChannel(String channelName, Client client) {
		boolean channelExists = false;
		for (Channel ch : channelList) {
			if (ch.getName().equals(channelName)) {
				channelExists = true;
				if (ch.getChannelOwner() == client) {
					channelList.remove(ch);
					client.send("\r\nSuccessfully deleted channel " + channelName + "\r\n");
					notify("delete", channelName, client);
				} else {
					client.send("\r\nPermission denied! You are not the owner of this channel.\r\n");
				}
			}
		}

		if (!channelExists)
			client.send("\r\nThere is no channel named " + channelName + "\r\n");

	}

	private void sendNewsMessage(String channelName, String message, Client client) {

		boolean channelExists = false;
		for (Channel ch : channelList) {
			if (ch.getName().equals(channelName)) {
				channelExists = true;
				if (ch.getChannelOwner() == client) {
					for (Client c : Client.getClients()) {
						if (ch.getSubscribers().contains(c)) {
							if (!isOffensive(message)) {
								c.send(message + "\r\n");
							} else {
								c.send("\r\n******Censored******\r\n");
							}
						}
					}
				} else {
					client.send("\r\nPermission denied! You are not the owner of " + channelName + "\r\n");
				}
			}
		}

		if (!channelExists)
			client.send("\r\nThere is no channel named " + channelName + "\r\n");

	}

	private void sendChannelList(Client client) {
		client.send("\r\nTo subscribe to a channel type: subscribe [channel_name]\r\n");
		client.send("---------------------- AVAILABLE CHANNELS -----------------\r\n");
		for (Channel ch : channelList) {
			client.send(ch.getName());
			if (ch.getDescription() != null)
				client.send(ch.getDescription());
			client.send("Number of subscribers: " + ch.getSubscribers().size());
		}
		client.send("\r\nType /help to list all available commands\r\n");
	}

	private void sendHelp(Client client) {
		client.send("\r\nlist channels -> Lists all the available channels" + "\r\nclose -> Closes your session"
				+ "\r\npublish [channel_name] " + "[channel_description --optional]" + " -> publishes a new channel"
				+ "\r\ndelete [channel_name] -> deletes a channel"
				+ "\r\nsubscribe [channel_name] -> Subscribes to a channel"
				+ "\r\nunsubscribe [channel_name] ->Unsubscribes from a channel" + "\r\nnews [channel_name] "
				+ "[your message]" + " -> Broadcasts news to a channel\r\n");
	}

	private void broadcastMessage(String message) {
		for (Client c : Client.getClients())
			if (!isOffensive(message))
				c.send(message + "\r\n");
			else
				c.send("\r\n******Censored******\r\n");
	}

	private boolean isOffensive(String message) {
		String lowerCaseMessage = message.toLowerCase();
		for (String forbiddenWord : forbiddenWords)
			if (lowerCaseMessage.indexOf(forbiddenWord) != -1)
				return true;
		return false;

	}

	private void notify(String action, String channelName, Client client) {
		String message = null;
		if (action.equals("publish"))
			message = "A new channel has been published: " + channelName + "\r\n";
		if (action.equals("delete"))
			message = channelName + " channel has been deleted\r\n";
		for (Client c : Client.getClients()) {
			if (c != client)
				c.send(message);
		}
	}

	private void handleClose(Client client) throws Exception {
		client.close();
	}

	@Override
	public void close() throws Exception {
		if (!executor.isTerminated()) {
			executor.shutdown();
		}
		if (!socket.isClosed()) {
			socket.close();
		}
	}

}

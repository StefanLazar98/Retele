package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class Client implements AutoCloseable {
	
	private static final List<Client> CONNECTIONS =
			Collections.synchronizedList(new ArrayList<Client>());
	private Socket socket;
	private BufferedReader reader;
	private PrintWriter writer;

	public Client(Socket socket) throws IOException {
		this.socket = socket;
		this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		this.writer = new PrintWriter(socket.getOutputStream());
		CONNECTIONS.add(this);
	}
	
	public void send(String message) {
		writer.println(message);
		writer.flush();
	}

	public String receive() throws IOException {
		return reader.readLine();
	}
	public boolean isClosed() {
		return socket.isClosed();
	}

	@Override
	public void close() throws Exception {
		if (!socket.isClosed()) {
			socket.close();
		}
		CONNECTIONS.remove(this);
	}

	public static List<Client> getClients() {
		return Collections.unmodifiableList(CONNECTIONS);
	}

}

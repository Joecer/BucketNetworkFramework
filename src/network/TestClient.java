package network;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;

import Common.Tool;
import network.bucketobject.Message;
import network.bucketobject.Query;
import network.bucketobject.USER;
import network.command.BucketCommand;
import network.command.client.ClientCommand;
import network.command.server.DataSaver;
import network.command.server.GetOnlineListCommand;
import network.command.server.MainCommand;
import network.connection.Connection;
import network.connection.UserConnection;
import network.listener.BucketListener;
import network.listener.ClientListener;
import network.listener.LoginListener;
import network.listener.MessageListener;
import network.listener.OnlineListListener;
import network.listener.QueryListener;

public class TestClient extends BucketListener {

	private UserConnection conn;
	private HashMap<Integer, ClientListener> business;
	private BucketListener listener;
	private MessageListener messageListener;

	public TestClient(String host, int port) throws UnknownHostException, IOException {
		business = new HashMap<Integer, ClientListener>();

		Socket s = new Socket(host, port);
		conn = new UserConnection(s, this);

		new Thread() {
			public void run() {
				try {
					conn.startListen();
				} catch (IOException e) {
					e.printStackTrace();
				}
			};
		}.start();
	}

	public void setListener(BucketListener listener) {
		this.listener = listener;
	}

	public BucketListener getListener() {
		return listener;
	}

	public void setMessageListener(MessageListener messageListener) {
		this.messageListener = messageListener;
	}

	public void getOnlineList(OnlineListListener listener) throws IOException {
		GetOnlineListCommand mc = new GetOnlineListCommand();

		if (listener != null) {
			int Sign = listener.hashCode();
			addBuss(Sign, listener);
			mc.setSign(Sign);
		}
		conn.send(mc.toServerCommand());
	}

	public void sendMessage(Message msg) throws IOException {
		conn.send(msg.toServerCommand());
	}

	@SuppressWarnings({ "unused", "unchecked" })
	public <T> void Query(Query q, QueryListener<T> listener) throws IOException {
		Class<T> entityClass = (Class<T>) ((ParameterizedType) listener.getClass().getGenericSuperclass())
				.getActualTypeArguments()[0];
		q.setTable_name(entityClass.getSimpleName());

		BucketCommand mm;
		if (listener != null) {
			int Sign = listener.hashCode();
			mm = q.toServerCommand(Sign);
			addBuss(Sign, listener);
		} else {
			mm = q.toServerCommand();
		}
		conn.send(mm);
	}

	public void Query(Query q) throws IOException {
		Query(q, null);
	}

	private void addBuss(int sign, ClientListener listener) {
		business.put(sign, listener);
	}

	private void removeBuss(int sign) {
		business.remove(sign);
	}

	public void Login(USER user, LoginListener listener) throws IOException {
		conn.login(user, listener);
	}

	public void Update(Object o) throws IOException {

		MainCommand mc = new MainCommand();

		DataSaver ds = new DataSaver();

		ds.setTable(Tool.object2Table(o));
		ArrayList<Object> a = new ArrayList<Object>();
		a.add(o);
		ds.setValues(Tool.List2JsonArray(a));
		mc.setCommand(ds.getClass().getName());
		mc.setValues(ds);

		conn.send(mc);
	}

	@Override
	public void onDataCome(Connection conn, String message) {

		UserConnection uconn = (UserConnection) conn;
		ClientCommand cm = Tool.JSON2E(message, ClientCommand.class);

		if (messageListener != null && cm.getCommand().equals(Message.class.getSimpleName())) {
			messageListener.onDataCome(conn, cm);
		} else if (uconn != null && uconn.getLoginListener() != null && cm.getCommand().equals("LOGIN")) {
			uconn.getLoginListener().onDataCome(uconn, cm);
		} else {
			if (cm.sign == 0) {

			} else {
				ClientListener l = business.get(cm.getSign());
				if (l != null) {
					l.onDataCome(conn, cm);
					removeBuss(cm.getSign());
				}
			}
		}

		if (listener != null)
			listener.onDataCome(uconn, message);

	}

	@Override
	public void onDisconnection(Connection conn) {
		if (listener != null)
			listener.onDisconnection(conn);
	}

}

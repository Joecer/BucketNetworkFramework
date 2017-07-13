package network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import Common.Gobal;
import Database.DatabaseManager;
import network.command.server.MainCommand;
import network.connection.UserConnection;
import network.listener.BucketListener;

public class TestServer {

	public TestServer(String MySQL_Username, String MySQL_Password, String DataBase,int port) throws IOException, ClassNotFoundException, SQLException 

	{
		SocketPool pool;
		DatabaseManager db;

		db = new DatabaseManager(MySQL_Username,  MySQL_Password, DataBase);
		pool = new SocketPool();

		Gobal.setDb(db);
		Gobal.setPool(pool);

		db.ConnectMySQL();

		@SuppressWarnings("resource")
		ServerSocket s = new ServerSocket(port);
		while (true) {
			Socket c = s.accept();

			final BucketListener listener = new BucketListener() {

				@Override
				public void onDataCome(network.connection.Connection c, String message) {
					UserConnection connection = (UserConnection) c;
					Gson gson = new GsonBuilder().create();

					try {
						MainCommand bo = gson.fromJson(message, MainCommand.class);
						bo.client = connection;
						bo.execute();
					} catch (IllegalStateException | com.google.gson.JsonSyntaxException e) {

					}

				}

				@Override
				public void onDisconnection(network.connection.Connection conn) {
					pool.remove(conn);

				}
			};

			pool.add(c, listener);

		}
	}
	
}

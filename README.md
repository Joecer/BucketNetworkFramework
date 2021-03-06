# Bucket网络应用框架
### 一个简单易用的网络应用框架

> 简单、快速地搭建一个网络服务，如：
>* 普通Socket服务:实时在线聊天，在线网络游戏等。
>* 简易Http服务:Web程序,HTTP代理等。
>* WebSocket服务:实时视频弹幕，数据实时监控等。
	
## 示例

### 创建一个Http服务

#### Application类

 应用类 服务接受的每一个客户都将创建一个Application对象进行事件处理。
>* 接口方法:
>
>* void onConnect(Connection connection); //握手完成后调用此方法
>* void onDataCome(Connection connection, byte data[]); //数据到达时调用此方法
>* void onDisconnect(Connection connection); //连接结束时调用此方法
>* void onException(Connection connection, Throwable e); //发生异常时调用此方法

示例:

```java
package demo;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Date;

import bucket.application.Application;
import bucket.network.Server;
import bucket.network.connection.Connection;
import bucket.network.protocol.HttpProtocol;

/**
 * HTTP应用示例
 * 
 * @author Hansin
 *
 */
public class HttpApplicationDemo extends Application {

	/**
	 * 默认文档目录
	 */
	public static String wwwroot = "D:\\Documents\\git\\stream-watch-web";

	/**
	 * 默认文件后缀
	 */
	public static String[] defaultFile = { "index.htm", "index.html", "index.php", "default.html" };

	/**
	 * 构造方法
	 * 
	 * @param server
	 *            传入Server对象
	 */
	public HttpApplicationDemo(Server server) {
		super(server);
	}

	@Override
	public void onConnect(Connection connection) {
		// 新的HTTP连接建立时，触发此方法

		HttpProtocol hp = (HttpProtocol) connection.getProtocol();// 将连接协议对象转换为HTTP协议

		String path = wwwroot + hp.getProtocolInfo().get("PATH"); // 获取请求的本地路径

		System.out.println(hp.toString());

		File f = new File(path);

		// 当请求路径为目录时，寻找默认文档
		if (f.isDirectory()) {

			for (int i = 0; ((!f.exists() || f.isDirectory()) && i < defaultFile.length); i++)
				f = new File(path + "/" + defaultFile[i]);// 遍历可用默认文档
		}

		try {

			if (f.exists()) {
				// 当文件存在时产生的响应

				// 发送 Http 200 状态
				hp.parseServerHeader("200 OK", null);

				// 发送数据
				BufferedInputStream in = new BufferedInputStream(new FileInputStream(f));
				int b;
				while ((b = in.read()) != -1)
					hp.write(b);
				in.close();
			} else {
				// 当文件不存在时产生的响应

				// 发送 Http 404 状态
				hp.parseServerHeader("404 Not Found", null);
				hp.send(("<div align='center'><h1>404 Not Found</h1><p>BNF v0.1</p><p>" + new Date() + "<p></div>")
						.getBytes());
			}

		} catch (Throwable e) {
			onException(connection, e);
		}

		try {
			hp.flush();
			hp.getSocket().close();
		} catch (Throwable e) {
			e.printStackTrace();
		}

	}

	@Override
	public void onDataCome(Connection connection, byte[] data) {

	}

	@Override
	public void onDisconnect(Connection conn) {

		// 连接断开时从线程池释放资源
		server.remove(conn);

	}

	@Override
	public void onException(Connection connection, Throwable e) {
		// 打印异常信息
		if (e.getClass().equals(SocketException.class) || e.getClass().equals(SocketTimeoutException.class))
			onDisconnect(connection);
		else
			e.printStackTrace();
	}

}
```
#### 创建HTTP服务

Application类只是规划了由客户产生的各种事件的行为蓝图，接下来是如何创建一个服务。

示例:

```java
package demo;

import bucket.network.Server;
import bucket.network.protocol.HttpProtocol;

/**
 * Http测试Demo
 * 
 * @author Hansin1997
 *
 */
public class HttpServerDemo {

	public static void main(String[] args) throws Throwable {

		//创建一个Server，绑定HttpApplicationDemo类，以及8080端口
		Server s = new Server(HttpApplicationDemo.class.getName(), 8080);
		
		//为Server添加支持的协议
		s.addProtocol(HttpProtocol.class);
		
		//启动服务，开始监听
		s.start();

	}
}
```

### 创建一个Http代理服务

#### Application类

示例:


```java
package demo.httpproxy;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;

import java.net.HttpURLConnection;

import java.net.Socket;
import java.net.URL;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import bucket.application.Application;
import bucket.network.Server;
import bucket.network.connection.Connection;

/**
 * Http代理应用Demo
 * 
 * @author Hansin
 *
 */
public class HttpProxyApplicationDemo extends Application {

	/**
	 * 中转等待标志
	 */
	protected boolean flag;

	public HttpProxyApplicationDemo(Server server) {
		super(server);
	}

	@Override
	public void onConnect(Connection connection) {
		System.out.println("代理用户连接    " + connection.getProtocol());
		try {
			String method = connection.getProtocol().getProtocolInfo().get("METHOD").toString();
			// 分析代理请求方法
			switch (method) {
			case "CONNECT":
				CONNECT(connection);
				break;
			default:
				GET(connection, method);
			}
		} catch (Throwable e) {

			e.printStackTrace();
		} finally {
			try {
				connection.getProtocol().flush();
			} catch (Throwable e) {
			}
		}

		onDisconnect(connection);
	}

	/**
	 * TCP代理（适用于HTTPS等加密方式）
	 * 
	 * @param connection
	 *            连接对象
	 * @throws Throwable
	 */
	protected void CONNECT(final Connection connection) throws Throwable {
		// 分析中转地址与端口
		String[] hp = (connection.getProtocol().getProtocolInfo().get("PATH").toString().split(":", 2));
		String host = hp[0];
		int port = (hp.length > 1 ? Integer.parseInt(hp[1]) : 443);

		// 建立中转连接
		Socket s = new Socket(host, port);

		// 设置中转连接超时
		s.setSoTimeout(5000);
		final BufferedInputStream in = new BufferedInputStream(s.getInputStream());
		final BufferedOutputStream out = new BufferedOutputStream(s.getOutputStream());

		// 输出代理成功信息
		connection.getProtocol().send("HTTP/1.1 200 Connection Established\r\n\r\n");

		flag = false; // 设置标志
		new Thread() {
			public void run() {
				int b;
				// 进行输出中转
				try {
					byte[] data = new byte[1024];
					while ((b = connection.getProtocol().read(data, 0, data.length)) != -1) {
						out.write(data, 0, b);
						out.flush();
					}

				} catch (Throwable e) {

				}

			};
		}.start();

		new Thread() {
			public void run() {
				int b;
				// 进行输入中转
				try {
					byte[] data = new byte[1024];
					while ((b = in.read(data, 0, data.length)) != -1) {

						connection.getProtocol().write(data, 0, b);
						connection.getProtocol().flush();

					}

				} catch (Throwable e) {

				}
				flag = true;

			};
		}.start();

		// 等待连接标志结束
		while (!flag)
			Thread.sleep(10);

		s.close();// 关闭中转连接

	}

	/**
	 * 普通HTTP代理
	 * 
	 * @param connection
	 *            连接对象
	 * @param method
	 *            请求方法：GET、POST等
	 */
	protected void GET(Connection connection, String method) {
		HttpURLConnection conn = null;
		int b;
		byte[] data = new byte[1024];

		try {

			// 建立中转Http连接
			conn = (HttpURLConnection) new URL(connection.getProtocol().getProtocolInfo().get("PATH").toString())
					.openConnection();

			conn.setRequestMethod(method);// 设置请求方法

			// 设置请求头
			for (Entry<String, String> kv : connection.getProtocol().getProtocolHeader().entrySet()) {
				conn.addRequestProperty(kv.getKey(), kv.getValue());
			}

			conn.setDoInput(true);

			// 如果方法为POST，为中转连接传入POST参数
			if (method.equals("POST")) {
				conn.setDoOutput(true);
				conn.connect();
				BufferedOutputStream out = new BufferedOutputStream(conn.getOutputStream());

				@SuppressWarnings("unchecked")
				Map<String, String> post = (Map<String, String>) connection.getProtocol().getProtocolInfo().get("POST");

				if (post != null) {
					Iterator<Entry<String, String>> it = post.entrySet().iterator();
					while (it.hasNext()) {
						Entry<String, String> kv = it.next();
						out.write((kv.getKey() + "=" + kv.getValue()).getBytes("UTF-8"));

						if (it.hasNext())
							out.write('&');

					}
					out.flush();
					out.close();
				}
			} else {
				conn.connect();
			}

			BufferedInputStream in = new BufferedInputStream(conn.getInputStream());

			// 输出连接状态
			connection.getProtocol()
					.write(("HTTP/1.1 " + conn.getResponseCode() + " " + conn.getResponseMessage() + "\r\n")
							.getBytes("UTF-8"));

			// 输出HTTP报头
			for (Entry<String, List<String>> kv : conn.getHeaderFields().entrySet()) {

				// 排除一些头
				if (kv.getKey() == null || kv.getKey().equals("Transfer-Encoding")) {
					continue;
				}

				for (String vs : kv.getValue()) {

					connection.getProtocol().write((kv.getKey() + ": " + vs + "\r\n").getBytes("UTF-8"));

				}

			}

			connection.getProtocol().send("\r\n");// 输出HTTP头结束标志

			// HTTP 数据部分传输
			while ((b = in.read(data, 0, data.length)) != -1) {
				connection.getProtocol().write(data, 0, b);
			}

			connection.getProtocol().flush();
			in.close();

		} catch (Throwable e) {
			// 输出错误
			try {
				connection.getProtocol().write(("HTTP/1.1 500 Error\r\n").getBytes("UTF-8"));
				connection.getProtocol().send("\r\n" + "<div align='center'><h1>500 Error</h1><p>" + e.toString()
						+ "</p><p>Bucket Netwrok Framework</p><p>" + new Date() + "</p></div>");
			} catch (Throwable e1) {

			}

		}
		conn.disconnect();// 关闭中转连接

	}

	@Override
	public void onDataCome(Connection connection, byte[] data) {

	}

	@Override
	public void onDisconnect(Connection connection) {
		server.remove(connection);// 释放资源
	}

	@Override
	public void onException(Connection connection, Throwable e) {
		e.printStackTrace();// 显示报错

	}
}
```

#### 创建HTTP代理服务

示例:

```java
package demo.httpproxy;

import bucket.network.Server;
import bucket.network.protocol.HttpProxyProtocol;

/**
 * Http代理测试Demo
 * 
 * @author Hansin1997
 *
 */
public class HttpProxyDemo {

	public static void main(String[] args) throws Throwable {

		// 创建服务，绑定HttpProxyApplicationDemo以及6666端口
		Server s = new Server(HttpProxyApplicationDemo.class.getName(), 6666);

		// 添加HttpProxyProtocol协议
		s.addProtocol(HttpProxyProtocol.class);

		// 监听
		s.start();

	}
}
```

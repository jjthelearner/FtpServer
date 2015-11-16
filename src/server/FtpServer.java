package server;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Random;
import java.util.StringTokenizer;


public class FtpServer {
	
	static int serverPort = 2222;//server listen to connection through this default port.
	static String logFileName = "Log.txt";//Server default Log file.
	static ServerSocket connectionListener = null; // A ServerSocket 
	static ArrayList<Account> authenticationList = null;//An ArrayList that is used to store authenication information. 
	
	public static void main(String args[]) throws IOException
	{
		if(args.length > 0) //see if user defines log file name.
		{
			logFileName = args[0];//change log file to user defined log file.
			if(args.length > 2)//see if user defines port number.
			{
				serverPort = Integer.parseInt(args[2]);//change port to user defined port.
			}
		}	

		connectionListener = new ServerSocket(serverPort);	
		loadAuthenticationInfo();//Load user login information from file to array authenticationList;
		outputToLogFile("Servser is initalized.");//Add server start time to log file.
		
		try {
			while(true)//A while that keeps listening and make a new thread for new connection.(multithreading)
			{
				ServerService newService = new ServerService(connectionListener.accept());//Once socket connection is established, create new serverService object.
				newService.start();//ServerService.run();
			}
			
		} finally {					
			connectionListener.close();//close listener when server is terminated. 	
			outputToLogFile("Servser is terminated.");//Add server terminated time to log file.
		}			

	}
	
	
	public static void loadAuthenticationInfo() throws IOException{
		String authenticationFileDirctory = "Authentication.txt"; //Directory of authentication file.
		
		File file = new File(authenticationFileDirctory);
		String line = null;
		FileInputStream inputStream = null;
		InputStreamReader inputStreamReader = null;
		BufferedReader bufferedReader = null;
		String userName = null;
		String passWord = null;
		
		try {
			inputStream = new FileInputStream(file);
			inputStreamReader = new InputStreamReader(inputStream);
			bufferedReader = new BufferedReader(inputStreamReader);	
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			outputToLogFile("Load authentication failed.");
			System.exit(-1);
		}
		authenticationList = new ArrayList<Account>();
		
		while((line = bufferedReader.readLine()) != null)//load authentication information into a list of account class object.
		{
			int seperator = line.indexOf(" ");
			userName = line.substring(0, seperator);//get user name.
			passWord = line.substring(seperator+1, line.length());//get password.
			
			Account account = new Account(userName, passWord);
			authenticationList.add(account);
		}
		outputToLogFile("Load authentication successfully.");
	}
	
	public static void outputToLogFile(String logMessage)
	{
		Date data = new Date();//message time stamp. 
		BufferedWriter logFileBufferedWriter = null;
		FileWriter logFileWriter = null;
		boolean append = false;//a boolean variable that indicates whether to create new log file or append message to an exist one.
		File logFile = new File(logFileName); 
		if(logFile.exists())//Try to open log file. If log file exists, set append to true. 
		{
			append = true;
		}
		try{
			logFileWriter = new FileWriter(logFileName,append); //Initialize file Writer.For append, if true then message will be add to the end of the file rather then the beginning.
			logFileBufferedWriter = new BufferedWriter(logFileWriter);//Initialize buffered writer.
			logFileBufferedWriter.write(data + ": " + logMessage);//output log message to log file.
			logFileBufferedWriter.newLine();//add new line
			logFileBufferedWriter.flush();//sent message from buffer to file.
			
			logFileBufferedWriter.close();//close file buffer writer.
			logFileWriter.close();//close file writer. 		
		}catch(IOException e)
		{
			
		}
		System.out.println(data + ": " + logMessage);
	}
	
}
	
	class ServerService extends Thread{
		
		String mode = ""; //define the FTP mode.(PASV, PORT, EPSV, EPRT)
		String clientIpAddress = ""; //Client IP address
		int clientPort = -1; 
		public Socket socket = null;
		BufferedReader input = null;
		BufferedWriter output = null;
		boolean modeChangeable = true; // When client has already selected a transmission mode and server has already open a port. user should not change mode until opened port has been used.(finish one transmission)
		
		String userName = null;
		Socket dataSocket = null;
		String fileDirectory = "dir/333"; //directory cannot start with back slash.
		
		//ServerService constructor
		public ServerService(Socket socket) throws IOException {
			this.socket = socket;
			socket.setSoTimeout(300000); //set socket timeout time. 
			try {
				input = new BufferedReader(new InputStreamReader(this.socket.getInputStream())); //construct bufferedReader.
				output = new BufferedWriter(new OutputStreamWriter(this.socket.getOutputStream()));//Construct bufferedWriter.
			} catch (IOException e) {
				String failedMessage = "Connection with " + clientIpAddress + " port: " + clientPort + "failed"; //Construct error message. 
				System.out.println(failedMessage);
				FtpServer.outputToLogFile(failedMessage);	//output error message to log file.
				interrupt();//stop the failed ftp connection thread.
			}
			

			
			
		}
	
		public void sendMessage(String messageToClient){
			try{
				output.write(messageToClient);
				output.flush();

			}catch(IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		public void run(){
			
			String clientResponse = null;
			sendMessage("220 Connect to FtpServer-jjin " + socket.getLocalAddress() + ":" + socket.getLocalPort()+"\r\n");
			Boolean validAccount = false;
			boolean running = true; //An indicator which will be assigned into false when client sends "QUIT".
			int NumOfAttempt = 0;//How many times that user has try authentication wrong.			
			String userName = null;
			String password = null;			
			System.out.println(this.socket.getInetAddress());
			
			
			
			while(!validAccount)
			{
				clientResponse = clientInput(); //waits for user input user name.
				if(NumOfAttempt == 3)//if user types in wrong authentication three times, the service will be end.
				{
					sendMessage("530 Cannot login.\r\n");
					running = false;
					validAccount = true; //Set validAccount to true to stop the loop.
					try {
						socket.close();
						input.close();
						output.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
				}else
				{
					switch(getCommandPrefix(clientResponse.toUpperCase()))
					{
					case "USER":
						userName = getCommandMessageOnly(clientResponse);
						sendMessage("331 Please specify the password.\r\n");
						break;
					case "PASS":
						if(userName == null)
						{
							sendMessage("503 Login with USER first.\r\n");//When user type password ahead of user name.
							clientResponse = clientInput();//wait and read again.
						}
						else
						{
							password = getCommandMessageOnly(clientResponse);//get password from client command.
							validAccount = checkAuthentication(userName,password);//return true if authentication is valid, and exit while loop.
							if(!validAccount)
							{
								NumOfAttempt++;
								userName = null;
							}
						}
						break;
					default: 
						sendMessage("530 Please login with USER and PASSWORD.\r\n");
					}
				}
			}
			
			while(running)
			{
				clientResponse = clientInput();
				switch(getCommandPrefix(clientResponse.toUpperCase())) //go to different function based on users' command.
				{
				case "QUIT":
					sendMessage("221 Goodbye.\r\n");
					FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + ") 221 Goodbye.");		
					FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + ") service end.");		
					running = false;//flag that end while loop
					break;
				case "HELP":
					ftp_HELP();
					break;
				case "LIST":
					ftp_LIST();
					break;
				case "PASV":
					ftp_PASV();
					break;
				case "PORT":
					ftp_PORT(getCommandMessageOnly(clientResponse));
					break;
				case "EPSV":
					ftp_EPSV();
					break;
				case "EPRT":
					ftp_EPRT(getCommandMessageOnly(clientResponse));
					break;	
				case "CWD":
					ftp_CWD(getCommandMessageOnly(clientResponse));
					break;
				case "PWD":
					ftp_PWD();
					break;
				case "CDUP":
					ftp_CDUP();
					break;
				case "RETR":
					try {
						ftp_RETR(getCommandMessageOnly(clientResponse));
					} catch (FileNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					break;
				default:
					sendMessage("500 Unkonw Command\r\n"); //send message to notify use that command does not make sense. 
				}	
			}	
			try {
				socket.close();
				input.close();
				output.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		




		private void ftp_CDUP() {
			int lastSlashPosition = fileDirectory.lastIndexOf("/");
			if(lastSlashPosition == -1)
			{
				fileDirectory = "";
			}
			else
			{
				fileDirectory = fileDirectory.substring(0,lastSlashPosition);
			}
			sendMessage("250 Directory successfully changed.\r\n");
			FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + "£© 250 Directory successfully changed.");
		}

		private void ftp_PWD() {
			if(fileDirectory == "")
			{
				sendMessage("257 \"/\"\r\n");
				FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + "£© 257 \"/\"");
			}
			else
			{
				sendMessage("257 \"/"+ fileDirectory+ "\"\r\n");
				FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + "£© 257 \"/"+ fileDirectory+ "\"");
			}

		}

		
		private void ftp_HELP() {
			sendMessage("214 The following commands are recognized: \n 	USER, PASS, CWD, CDUP, QUIT, PASV, EPSV, PORT, EPRT, RETR, PWD, LIST,HELP.\r\n");
			sendMessage("214 Help OK.\r\n");
			FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + ") 214 Send help list.");
			
		}

		private void ftp_RETR(String clientResponse) throws FileNotFoundException {
			if(clientResponse.equals("")){ //if the file name is empty. 
				sendMessage("450 Requested file action not taken.\r\n");
				FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + ") 450 Requested file action not taken.");	
			}
			else
			{
				File file = new File(fileDirectory + "/" + clientResponse);
				if(!file.exists())//if directory does not exist. 
				{
					sendMessage("550 CWD failed. File unavailable.\r\n");
					FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + ") 550 CWD failed. File unavailable.");
					return;
				}
				
				sendMessage("150 File status okay; about to open data connection.\r\n");//send message to connection port.
				FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + ") 150 File status okay; about to open data connection.");
				
				RandomAccessFile selectedFile = null;//the file that want to be downloaded.
				OutputStream outputStream= null;
				
				try {
					selectedFile = new RandomAccessFile(fileDirectory + "/" + clientResponse, "r");
					outputStream = this.dataSocket.getOutputStream(); //initial output stream.
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
				
                byte fileBuffer[]= new byte[1024];   
                int length;  
                
                try {
					while((length = selectedFile.read(fileBuffer)) != -1) //see if is the end of the file
					{
						try {
							outputStream.write(fileBuffer, 0 , length);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                
                try {
					outputStream.close();
	                selectedFile.close();
	                dataSocket.close();
	                dataSocket = null;
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                sendMessage("226 Transfer OK\r\n");
				FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + ") 226 Transfer OK");

			}
		}

		private void ftp_CWD(String clientResponse) {
			String newDir = fileDirectory + "/" + clientResponse; //new working directory.
			File file = new File(newDir);
			if(!file.exists())//if directory does not exist. 
			{
				sendMessage("550 CWD failed. File unavailable.\r\n");
				FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + ") 550 CWD failed. File unavailable.");
			}
			else {
				fileDirectory = newDir;
				sendMessage("250 Directory successfully changed.\r\n");
				FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + ") 250 Directory successfully changed.");
			}
				
			
		}

		private void ftp_LIST() {
			
			if(this.dataSocket == null)
			{
				sendMessage("425 Use PORT or PASV first.\r\n");//If have not choose ftp transmission mode, choose one first. 
				FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + ") 425 Use PORT or PASV first.");		
			}
			else
			{
				PrintWriter listPrintWriter = null;
				try {
					listPrintWriter = new PrintWriter(this.dataSocket.getOutputStream(),true);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + ") Unable to sent LIST.");		
				}
				String fileDate = null; //File last modified time.
				File directory = new File(this.fileDirectory); //open the file directory on ftp server.
				File[] filesList = directory.listFiles(); //put files in the directory into a file list.
				
				for(int i = 0; i < filesList.length;i++)
				{
					fileDate = new SimpleDateFormat("mm/dd/yyyy").format(new Date(filesList[i].lastModified()));//Formatting the modified data of the file.
					if(filesList[i].isFile()){
						listPrintWriter.println("-rw-r--r--	1	ftp	ftp	" + filesList[i].length() + "	" + fileDate + "	" + filesList[i].getName() );
					}
					else {
						listPrintWriter.println("drwxrwxr-x	2	ftp	ftp	" + filesList[i].length() + "	" + fileDate + "	" + filesList[i].getName() );
					}
					listPrintWriter.flush();
				}
				try {
					dataSocket.close();
					listPrintWriter.close();
					dataSocket = null;
				} catch (IOException e) {
					// TODO Auto-generated catch block
					FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + ") LIST transfer failed");		
				}
				sendMessage("226 Directory send OK.\r\n");
				FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + ") 226 Directory send OK.");	
			}				
		}

		private void ftp_EPRT(String clientResponse) {
			String dataIP = null;
			int dataPort = -1;
			
			StringTokenizer tokenizer = new StringTokenizer(clientResponse, "|");
			String useless = tokenizer.nextToken(); //get rid of first argument.
			dataIP = tokenizer.nextToken(); // get ip address.
			dataPort = Integer.parseInt(tokenizer.nextToken()); //Get port number.
			
			System.out.println(dataIP + "    " + dataPort);
			try {
				this.dataSocket = new Socket(dataIP, dataPort);
				sendMessage("220 port command successful. In EPRT mode.\r\n");
				FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + ") 220 port command successful. In EPRT mode.");		
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				sendMessage("425 Can¡¯t open data connection.\r\n");
				FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + ") 425 Can¡¯t open data connection.");		
			}
		}

		private void ftp_EPSV() {
			int randomPort = portNumGenerator();//Generate a random port.
			sendMessage("229 Entering Extended Passive Mode (|||"+randomPort+"|).\r\n");//send client EPSV address information.
			FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + ") using EPSV mode : "+ "229 Entering Extended Passive Mode (|||"+randomPort+"|).");
			
			ServerSocket trasmissionSocket = null;
			try {
				trasmissionSocket = new ServerSocket(randomPort);
			} catch (IOException e) {
				FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + ") failed to connection PASV port " + socket.getLocalPort() + ":" + randomPort + ".");
				currentThread().stop();
			}
			try {
				this.dataSocket = trasmissionSocket.accept();//Initialize data port. 
				trasmissionSocket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + ") failed to connection PASV port " + socket.getLocalPort() + ":" + randomPort + ".");
			}
		}

		private void ftp_PORT(String clientResponse) {
			String dataIp = null;
			int dataPort = -1;
			StringTokenizer tokenizer = new StringTokenizer(clientResponse, ",");//parse ip address by ,
			dataIp = tokenizer.nextToken() + "." + tokenizer.nextToken() + "." +tokenizer.nextToken() + "." +tokenizer.nextToken();
			dataPort = Integer.parseInt(tokenizer.nextToken())*256 +Integer.parseInt(tokenizer.nextToken());
			
			try {
				this.dataSocket = new Socket(dataIp, dataPort);
				sendMessage("220 port command successful.\r\n");
				FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + ") 220 port command successful.");		
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				sendMessage("425 Can¡¯t open data connection.\r\n");
				FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + ") 425 Can¡¯t open data connection.");		
			}
		}
		
		

		private void ftp_PASV() {
//			if(!this.mode.isEmpty()||!modeChangeable)//see if mode has already set, and mode has been initialized by server. 
//			{
//				sendMessage("503 mode has been selected.\r\n");
//				FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + ") has a bad request on mode.");
//				return;
//			}
			//this.mode = "PASV";
			//modeChangeable = false;
			int randomPort = portNumGenerator();
			int one = randomPort / 256;//First part of pasv port.
			int two = randomPort % 256;//Second part of pasv port.
			String address = socket.getLocalAddress().toString(); //get local ip addrtess.
			String localHostAddress = address.substring(1, address.length()); //get rid of "/". 
			String pasvAddress = "(" + localHostAddress.replace(".", ",") + "," + one + "," + two + ")";
			sendMessage("227 Entering Passive Mode " + pasvAddress + ".\r\n");//send PASV address information to client. 
			FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + ") using PASV mode"+ pasvAddress + ".");//output log
			
			ServerSocket trasmissionSocket = null;
			try {
				trasmissionSocket = new ServerSocket(randomPort);
			} catch (IOException e) {
				FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + ") failed to connection PASV port " + socket.getLocalPort() + ":" + randomPort + ".");
				currentThread().stop();
			}
			
			
			try {
				this.dataSocket = trasmissionSocket.accept();//Initialize data port. 
				System.out.println("The data transmission socket has been established.");
				trasmissionSocket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + ") failed to connection PASV port " + socket.getLocalPort() + ":" + randomPort + ".");
			}
		}

		
		
		int portNumGenerator()//randomly generate a port num (20000~50000)
		{
			Random random = new Random();
			int randNum = random.nextInt(20000)+30000;
			return randNum;
		}
		
		
		
		boolean checkAuthentication(String userName, String password) { // check the authentication of the log in.
			String UN = null;
			String PW = null;
			for(Iterator i = FtpServer.authenticationList.iterator();i.hasNext();)
			{
				Account account = (Account)i.next();
				UN = account.userName;
				PW = account.passWord;
				
				if(UN.equals(userName) && PW.equals(password))
				{
					sendMessage(" 230 Login successful.\r\n"); //return message that login successful.
					sendMessage("230 Welcome to Jiakang Jin's Ftp Server!!!\r\n");//return message that login successful.

					FtpServer.outputToLogFile("Client: " + userName + "(" + socket.getInetAddress() + ":" + socket.getPort() + ") is logged in."); //Log that client has logged in successfully.
					this.userName  = userName;
					return true;
				}
			}
			sendMessage("530 Login incorrect.\r\n");
			return false;
		}

		public String clientInput()
		{
			String ClientResponse = null;
			try {
				ClientResponse = input.readLine(); //read from client input
			} catch (IOException e) {
				FtpServer.outputToLogFile("Failed to receive client " + userName +" message");
				interrupt();
			}
			return ClientResponse;
		}
		
		
		static String getCommandPrefix(String clientCommand)//Get the first four letters ftp command from user command.
		{
			int seperator = clientCommand.indexOf(" ");//separate command by " ".
			String ftpCommand = null;
			if(seperator == -1)//If user command does not contain " " (garbage command).
			{
				ftpCommand = clientCommand;
			}
			else
			{
				ftpCommand = clientCommand.substring(0,seperator);
			}
			return ftpCommand;
		}
		
		static String getCommandMessageOnly(String clientCommand) //get content of client input besides ftp command.
		{
			int seperator = clientCommand.indexOf(" ");
			String clientMessage = null;
			if(seperator == -1)
				clientMessage = "";
			else
				clientMessage = clientCommand.substring(seperator+1,clientCommand.length());
			return clientMessage;
		}
		
		
		
		
		public static void sendDirectory(){
			
		}
		
		
		
	}
	
	 class Account{
		String userName;
		String passWord;
		public Account(String userName, String passWord) {
			this.userName = userName;
			this.passWord = passWord;
		}
	}
	
	
	


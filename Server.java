import java.net.*;
import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

class Server {
    static ServerSocket ss = null;
    static Vector<ClientThread> clients = new Vector<>();
    static boolean gameStarted = false;
    static boolean voteStarted = false;
    static ArrayList<Integer> voteList = new ArrayList<Integer>();
    static int voteCount = 0;
    static boolean duplicate = false;
    static int selected = -1;
    static int selectedID = -1;
    static long start; 
    static long end;
    static int wolf_id;
    static TimeThread tt; 
    static String url = "http://localhost:5000/get_random";
    static String themes[];


    public static void main(String args[]) {
        try {
            ss = new ServerSocket(1234, 300);

            while (true) {
                try {
                    
                    Socket s = ss.accept();
                    ClientThread ct = new ClientThread(s);
                    ct.start();
                    if (!gameStarted){
                        voteList.add(0);
                        clients.add(ct);
                        sendPlayer(ct);
                    } else {
                        ct.sendMessage("ALREADY_STARTED");
                    }
                    if (clients.size() >= 4 && !gameStarted){
                        startGame();
                    }
                } catch (IOException e) {
                    System.err.println("Caught IOException");
                    System.exit(1);
                }
            }
        } catch (IOException e) {
            System.err.println("Caught IOException");
            System.exit(1);
        }
    }

    public static void broadcast(String message, ClientThread sender) {
        for (ClientThread client : clients) {
            client.sendMessage(message);
        }
    }

    public static void startGame() {
        if (clients.size() >= 4 && !gameStarted) {
            gameStarted = true;
            tt = new TimeThread();
            tt.start();
        }
    }

    public static void sendPlayer( ClientThread sender){
        String players = "PLAYER_LIST";
        for (int i = 0; i < clients.size()-1; i++) {
            players += " ";
            players += clients.get(i).getClientName();
        }
        players += " END";
        sender.sendMessage(players);
    }

    public static void restart(){
        start = System.currentTimeMillis();
        voteStarted = false;
        voteCount = 0;
        voteList.clear();
        for (int i = 0 ; i<clients.size(); i++){
            voteList.add(0);
        }
        duplicate = false;
    }

    static class TimeThread extends Thread{

        public TimeThread() {
            try {
		        Thread.sleep( 10000 );
            } catch ( InterruptedException e ) {
	            e.printStackTrace();			
	            return;
            }
            start = System.currentTimeMillis();
            Random random = new Random();
            wolf_id = random.nextInt(clients.size());
            HttpClient http_client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

            try {
                HttpResponse<String> response = http_client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                themes = response.body().split(" ");
                System.out.println("小数派 " + themes[0]);
                System.out.println("多数派 " + themes[1]);
            } catch (Exception e) {
                e.printStackTrace();
            }
            for (int i = 0; i < clients.size(); i++) {
                if (i == wolf_id) {
                    clients.get(i).sendMessagePrivate("THEME " + themes[0]);
                } else {
                    clients.get(i).sendMessagePrivate("THEME " + themes[1]);
                }
            }

            while(true){
                end = System.currentTimeMillis();
                //System.out.println(end - start);
                if (end - start > 20000 && !voteStarted){
                    for (ClientThread client : clients) {
                        client.sendMessage("VOTE");
                        for (int i = 0; i < clients.size(); i++) {
                            client.sendMessage("id " + i + " " + clients.get(i).getClientName());
                        }
                        voteStarted = true;
                    }
                }      
            }
        }

        
    }


    static class ClientThread extends Thread {
        Socket socket;
        OutputStream sout;
        InputStream sin;
        BufferedReader br;
        PrintWriter pw;
        String clientName;
        private int vote;

        public ClientThread(Socket socket) throws IOException {
            this.socket = socket;
            sout = socket.getOutputStream();
            sin = socket.getInputStream();
            br = new BufferedReader(new InputStreamReader(sin));
            pw = new PrintWriter(new OutputStreamWriter(sout), true);
            vote = -1;

        }


        public String getClientName() {
            return clientName;
        }

        public void sendMessage(String message) {
            pw.println(message);
        }

        public void sendMessagePrivate(String message) {
            //pw.println("PRIVATE_MESSAGE");
            pw.println(message);
        }

        public int getVote() {
            return vote;
        }

        public void resetVote() {
            vote = -1;
        }

        public void checkVote(String message){
            try {
                int id = Integer.parseInt(message);
	            if ( id < clients.size() && id >= 0 ){
                    vote = id;
                    voteList.set(id, voteList.get(id) + 1);
                    voteCount += 1;
                    System.out.println("voteCount" + voteCount);
                    System.out.println(voteList);
                    if (voteCount == clients.size()){
                        for (int i = 0; i < clients.size(); i++) {
                            if (voteList.get(i) > selected ){
                                selectedID = i;
                                selected = voteList.get(i);
                                duplicate = false;
                            } else if (voteList.get(i) == selected ){
                                duplicate = true;
                            }
                        }

                        if (duplicate){
                            for (int i = 0; i < clients.size(); i++){
                                clients.get(i).sendMessagePrivate("RESTART");
                                clients.get(i).resetVote();
                            }
                            restart();
                        } else if (selectedID == wolf_id){
                            System.out.println("SELECTED " + clients.get(selectedID).getClientName());
                            for (int i = 0; i < clients.size(); i++) {
                                if (i == wolf_id) {
                                    clients.get(i).sendMessagePrivate("SELECTED " + clients.get(selectedID).getClientName() +" LOSE");
                                } else {
                                    clients.get(i).sendMessagePrivate("SELECTED " + clients.get(selectedID).getClientName()+" WIN");
                                }
                            }
                        } else {
                            for (int i = 0; i < clients.size(); i++) {
                                if (i == wolf_id) {
                                    clients.get(i).sendMessagePrivate("SELECTED " + clients.get(selectedID).getClientName()+" WIN");
                                } else {
                                    clients.get(i).sendMessagePrivate("SELECTED " + clients.get(selectedID).getClientName()+" LOSE");
                                }
                            }

                        }
                    }
                    
                } else {
	                this.sendMessagePrivate("INVALID");
                }
            } catch (NumberFormatException e) {
	            this.sendMessagePrivate("INVALID");
            }
        }

        public void run() {
            try {
                // クライアントからの名前の受け取り
                clientName = br.readLine();
                broadcast("JOIN " + clientName, this);
                String str;

                while ((str = br.readLine()) != null) {
                    System.out.println(clientName + ": " + str);
                    if (voteStarted && vote == -1){
                        checkVote(str);
                    } else {
                        broadcast(clientName + ": " + str, this);
                    }

                }
            } catch (IOException e) {
                System.err.println("Caught IOException");
            } finally {
                // クライアントが切断された場合の処理
                int remove_id  = clients.indexOf(this);
                clients.remove(this);
                broadcast("REMOVE " + clientName + " " + remove_id, this);
            }
        }
    }
}

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class Replica_UDP {
    static final Path ARQUIVO = Paths.get("temperaturas_replica.txt");

    public static void main(String[] args) throws Exception {
        
        int porta = args.length > 0 ? Integer.parseInt(args[0]) : 9871;
        int versao = 0;
        
        if (!Files.exists(ARQUIVO)) Files.write(ARQUIVO, "(sem dados - aguardando primeira sincronização)\n".getBytes(StandardCharsets.UTF_8));

        DatagramSocket socket = new DatagramSocket(porta);
        byte[] buf = new byte[65000];
        System.out.println("RÉPLICA EM AÇÃO na porta " + porta);

        while (true) {
           
            DatagramPacket in = new DatagramPacket(buf, buf.length);
            socket.receive(in);
            String msg = new String(in.getData(), 0, in.getLength(), StandardCharsets.UTF_8);
            String resposta;

            if (msg.startsWith("SYNC:")) {
                
                int nl = msg.indexOf('\n');
                versao = Integer.parseInt(msg.substring(5, nl).trim());
                Files.write(ARQUIVO, msg.substring(nl + 1).getBytes(StandardCharsets.UTF_8));
                System.out.println("[SYNC] atualizada para versão " + versao);
                resposta = "ACK:" + versao;

            } else if (msg.trim().equalsIgnoreCase("GET")) {

                System.out.println("GET de " + in.getAddress() + " (servindo versão " + versao + ")");
                resposta = "VERSAO:" + versao + "\n" + new String(Files.readAllBytes(ARQUIVO), StandardCharsets.UTF_8);
            
            } else {
             
                resposta = "ERRO: comando desconhecido (use GET)";
            
            }
          
            byte[] out = resposta.getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(out, out.length, in.getAddress(), in.getPort()));
        
        }
    }
}

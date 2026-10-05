import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalTime;

public class Cliente_UDP {

    static String IP_SERVIDOR = "localhost";
    static int PORTA_SERVIDOR = 9870;
    static String IP_REPLICA = "localhost";
    static int PORTA_REPLICA = 9871;
    static final int TIMEOUT_MS = 3000;

    public static void main(String[] args) throws Exception {
        if (args.length > 0) IP_SERVIDOR = args[0];
        if (args.length > 1) IP_REPLICA = args[1];

        BufferedReader teclado = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(TIMEOUT_MS);

        System.out.println("Servidor: " + IP_SERVIDOR + ":" + PORTA_SERVIDOR
                + " | Replica: " + IP_REPLICA + ":" + PORTA_REPLICA);

        while (true) {
            System.out.println();
            System.out.println("1 - Baixar do Servidor Principal");
            System.out.println("2 - Baixar da Replica");
            System.out.println("3 - Baixar dos dois e comparar versoes");
            System.out.println("0 - Sair");
            System.out.print("Opcao: ");
            String op = teclado.readLine();
            if (op == null || op.trim().equals("0")) break;

            switch (op.trim()) {
                case "1": baixar(socket, "servidor", IP_SERVIDOR, PORTA_SERVIDOR); break;
                case "2": baixar(socket, "replica", IP_REPLICA, PORTA_REPLICA); break;
                case "3":
                    int vs = baixar(socket, "servidor", IP_SERVIDOR, PORTA_SERVIDOR);
                    int vr = baixar(socket, "replica", IP_REPLICA, PORTA_REPLICA);
                    if (vs >= 0 && vr >= 0) {
                        System.out.println();
                        if (vs == vr) System.out.println(">> CONSISTENTE: ambos na versao " + vs);
                        else System.out.println(">> DESATUALIZADA: servidor v" + vs + " x replica v" + vr);
                    }
                    break;
                default: System.out.println("Opcao invalida.");
            }
        }
        socket.close();
    }

    static int baixar(DatagramSocket socket, String origem, String ip, int porta) {
        try {
            byte[] req = "GET".getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(req, req.length, InetAddress.getByName(ip), porta));

            byte[] buffer = new byte[65000];
            DatagramPacket resp = new DatagramPacket(buffer, buffer.length);
            socket.receive(resp);

            String msg = new String(resp.getData(), 0, resp.getLength(), StandardCharsets.UTF_8);
            if (!msg.startsWith("VERSAO:")) {
                System.out.println("Resposta inesperada de " + origem + ": " + msg);
                return -1;
            }
            int nl = msg.indexOf('\n');
            int versao = Integer.parseInt(msg.substring(7, nl).trim());
            String conteudo = msg.substring(nl + 1);

            String arquivo = "download_" + origem + ".txt";
            Files.write(Paths.get(arquivo), conteudo.getBytes(StandardCharsets.UTF_8));

            System.out.println();
            System.out.println("[" + LocalTime.now().withNano(0) + "] " + origem.toUpperCase()
                    + " - versao " + versao);
            System.out.print(conteudo);
            System.out.println("Salvo em " + arquivo);
            return versao;
        } catch (SocketTimeoutException e) {
            System.out.println("Sem resposta de " + origem + " (timeout). Verifique IP, porta e firewall.");
        } catch (Exception e) {
            System.out.println("Erro: " + e.getMessage());
        }
        return -1;
    }
}
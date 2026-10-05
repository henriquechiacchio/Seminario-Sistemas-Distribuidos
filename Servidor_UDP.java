import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;


public class Servidor_UDP {

    static int INTERVALO_X_SEGUNDOS = 15;         
    static String IP_REPLICA = "localhost";     
    static int PORTA_REPLICA = 9871;
    static final int PORTA_SERVIDOR = 9870;
    static final Path ARQUIVO = Paths.get("temperaturas_servidor.txt");

    static int versao = 1;
    static boolean pendente = true;              
    static final Object LOCK = new Object();

    public static void main(String[] args) throws Exception {
        if (args.length > 0) INTERVALO_X_SEGUNDOS = Integer.parseInt(args[0]);
        if (args.length > 1) IP_REPLICA = args[1];
        if (args.length > 2) PORTA_REPLICA = Integer.parseInt(args[2]);

        if (!Files.exists(ARQUIVO)) criarArquivoInicial();

        // Timer
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        timer.scheduleAtFixedRate(Servidor_UDP::replicar,
                INTERVALO_X_SEGUNDOS, INTERVALO_X_SEGUNDOS, TimeUnit.SECONDS);

        // Console para alterar temperaturas
        Thread console = new Thread(Servidor_UDP::console);
        console.setDaemon(true);
        console.start();

        DatagramSocket socket = new DatagramSocket(PORTA_SERVIDOR);
        byte[] buf = new byte[1024];
        System.out.println("SERVIDOR EM AÇÃO na porta " + PORTA_SERVIDOR
                + " | x = " + INTERVALO_X_SEGUNDOS + "s | réplica = " + IP_REPLICA + ":" + PORTA_REPLICA);

        while (true) {
            DatagramPacket in = new DatagramPacket(buf, buf.length);
            socket.receive(in);
            String msg = new String(in.getData(), 0, in.getLength(), StandardCharsets.UTF_8).trim();
            System.out.println("RECEIVE de " + in.getAddress() + ": " + msg);

            String resposta;
            if (msg.equalsIgnoreCase("GET")) {
                synchronized (LOCK) { resposta = "VERSAO:" + versao + "\n" + ler(); }
            } else {
                resposta = "ERRO: comando desconhecido (use GET)";
            }
            byte[] out = resposta.getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(out, out.length, in.getAddress(), in.getPort()));
        }
    }

    static void replicar() {
        String conteudo; int v;
        
        synchronized (LOCK) {
            if (!pendente) { System.out.println("[TIMER] sem alterações, nada a replicar."); return; }
            conteudo = ler(); v = versao; pendente = false;
        }

        try (DatagramSocket s = new DatagramSocket()) {
        
            s.setSoTimeout(2000);
            byte[] data = ("SYNC:" + v + "\n" + conteudo).getBytes(StandardCharsets.UTF_8);
            s.send(new DatagramPacket(data, data.length, InetAddress.getByName(IP_REPLICA), PORTA_REPLICA));
            byte[] b = new byte[256];
            DatagramPacket ack = new DatagramPacket(b, b.length);
            s.receive(ack);
            System.out.println("[TIMER] réplica atualizada para versão " + v + " (" +
                    new String(ack.getData(), 0, ack.getLength(), StandardCharsets.UTF_8).trim() + ")");

        } catch (Exception e) {

            synchronized (LOCK) { pendente = true; }   // tenta de novo no próximo ciclo
            System.out.println("[TIMER] falha ao replicar: " + e.getMessage());
        
        }
    }

    static void console() {

        System.out.println("Console: digite  Cidade=valor | listar | sync | sair");
        
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
        
            String l;
        
            while ((l = br.readLine()) != null) {
                l = l.trim();
                if (l.equalsIgnoreCase("listar")) { synchronized (LOCK) { System.out.println(ler()); } }
                else if (l.equalsIgnoreCase("sync")) replicar();
                else if (l.equalsIgnoreCase("sair")) System.exit(0);
                else if (l.contains("=")) {
                    String[] p = l.split("=", 2);
                    try { alterar(p[0].trim(), p[1].trim()); }
        
                    catch (Exception e) { System.out.println("Erro: " + e.getMessage()); }
                }
            }

        } catch (IOException e) { System.out.println("Console: " + e.getMessage()); }
    }

    static void alterar(String cidade, String valor) throws IOException {
        
        Double.parseDouble(valor.replace(',', '.'));
        
        synchronized (LOCK) {
            
            Map<String, String> m = new LinkedHashMap<>();
            
            for (String linha : Files.readAllLines(ARQUIVO, StandardCharsets.UTF_8)) {
                String[] p = linha.split(";", 2);
                if (p.length == 2) m.put(p[0], p[1]);
            }

            if (!m.containsKey(cidade)) throw new IOException("cidade não encontrada: " + cidade);

            m.put(cidade, valor);
            StringBuilder sb = new StringBuilder();
            m.forEach((k, v) -> sb.append(k).append(';').append(v).append('\n'));
            Files.write(ARQUIVO, sb.toString().getBytes(StandardCharsets.UTF_8));
            versao++; pendente = true;
            System.out.println("[SERVIDOR] " + cidade + " = " + valor + " (versão " + versao
                    + "; será replicado em até " + INTERVALO_X_SEGUNDOS + "s)");
        }
    }

    //Le e cria arquivo incial caso não exista
    static String ler() {
        
        try { return new String(Files.readAllBytes(ARQUIVO), StandardCharsets.UTF_8); }
        catch (IOException e) { return "ERRO ao ler arquivo: " + e.getMessage(); }
    }

    static void criarArquivoInicial() throws IOException {

        String s = "Vitória da Conquista;21.5\nJequié;27.0\nGuanambi;28.5\nBrumado;26.0\n"
                 + "Itapetinga;25.0\nBarra da Estiva;22.0\nLivramento de Nossa Senhora;24.5\n";
        Files.write(ARQUIVO, s.getBytes(StandardCharsets.UTF_8));
    }
}

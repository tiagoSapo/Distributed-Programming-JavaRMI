package modelo;

import modelo.comunicacao.*;
import java.net.*;
import java.util.*;
import java.io.*;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;


public class Cliente implements ComunicacaoClienteServidor {

    private ServerSocket socket_transferencias; // socket de escuta para efetuar o upload de ficheiros (usado pela Thread 'TransferenciasServidor')
    private Socket socket_comunicacao, socket_notificacoes_tcp; // socket_comunicacao -> socket de comunicacao com o servidor (tambem conhecido por 'socket metodos') 
    private ObjectInputStream in, in_notificacoes; // in -> inputstream do socket 'socket_comunicao', in_notificacoes -> inputstream do socket 'notificacoes_tcp'
    private ObjectOutputStream out; // out -> outputstream do socket 'socket_comunicao'
    
    private DatagramSocket socket_notificacoes_udp; // Socket para notificacoes UDP
    
    private ServidorNotificacoesTCP thread_tcp; // handle para Thread que recebe notificacoes TCP do servidor
    private ServidorNotificacoesUDP thread_udp; // handle para Thread que recebe notificacoes UDP do servidor
    private TransferenciasServidor thread_upload; // handle para Thread responsavel por tratar os pedidos de upload por parte de outros clientes (esta thread assim que recebe uma ligacao TCP, inicia uma nova thread dentro dela para o cliente que se ligou a este cliente)
    private final ArrayList<TransferenciasCliente> threads_download = new ArrayList<>(); // array com todas as threads que estao a fazer download de ficheiros
    private DiretoriaThread thread_diretoria; // handle para Thread que contem o WATCHSERVICE
    
    // Dados do cliente actual (username, password e diretoria dos ficheiros a partilhar)
    private String USERNAME;
    private String PASSWORD;
    private String DIRETORIA;
    
    // Metodos para gerir conta
    private void resetConta() {
        USERNAME = null;
        PASSWORD = null;
        DIRETORIA = null;
    }
    private void loginConta(String username, String password, String dir) {
        USERNAME = username;
        PASSWORD = password;
        DIRETORIA = dir;
    }
    private boolean existeConta() {
        return !(USERNAME == null || PASSWORD == null);
    }
 
    // Metodos a thread principal 'main'
    public void iniciar() throws UnknownHostException {

        Scanner sc = new Scanner(System.in);

        System.out.println("--------------- CLIENTE ---------------");
        System.out.println("IP Servidor: ");
        System.out.print("> ");
        String ip_servidor = sc.nextLine();
        
        System.out.println("Porto TCP: ");
        System.out.print("> ");
        int porto_servidor = -1;
        
        try { 
            porto_servidor = Integer.valueOf(sc.nextLine());
        } 
        catch (NumberFormatException e) {
            System.out.println(porto_servidor);
            System.out.println("[ERRO] Porto inválido: " + e);
        }

        if (!ligarAoServidor(ip_servidor, porto_servidor))
            return;

        if (!ligarMeuServidorTransferencias())
            return;
        
        atendePedidos(); 
    }
    private String getComandos() {
        
        // Os comandos comentados funcionam!
        
        String str = "";
        
        str += "\nEscrever um dos seguintes comandos:" + "\n";
        str += "1. registar nome username palavra-passe diretoria_fich" + "\n";
        str += "2. autenticar username palavra-passe diretoria_fich" + "\n";
        str += "3. remover username palavra-passe" + "\n";
        str += "4. desautenticar" + "\n";
        /*str += "5. novoficheiro nome_ficheiro" + "\n";*/ /* Forca a adicao de um ficheiro 'a base de dados */
        /*str += "6. removerficheiro nome_ficheiro" + "\n";*/ /* Forca a remocao de um ficheiro 'a base de dados */
        str += "7. utilizadores" + "\n";
        str += "8. enviar utilizador_destino titulo descricao" + "\n";
        str += "9. broadcast titulo descricao" + "\n";
        /*str += "10. download nome_ficheiro" + "\n";*/ /* devolve o porto do cliente que possui o ficheiro */
        str += "11. tranferir nome_ficheiro novo_nome" + "\n";
        str += "12. historial" + "\n";
        
        return str;
    }
    private void atendePedidos() {
        
        Scanner sc = new Scanner(System.in);
        
        System.out.println(getComandos());
        String input = "\n> ";
        
        
        while (true) {

            if (existeConta()) {
                
                Utilizadores u;
                try {
                    if (autenticadoUtilizador(USERNAME)) {
                        // Falhas UDP superior a 3
                        System.out.println("\n[Info] Ocorreu um problema no lado do servidor, a terminar programa...");
                    }
                } 
                catch (Exception e) {
                    // Falhas UDP
                    System.out.println("\n[Info] Numero de falhas de ligacao UDP excederam as 3, a terminar programa..." + e);
                    break;
                }
            }

            System.out.print(input);
            
            String texto = sc.nextLine();
            if (texto.equals("")) {
                System.out.println("[ERRO] Comando vazio. Escrever 'help' para consultar todos os comandos");
                continue;
            }
            
            StringTokenizer cmds = new StringTokenizer(texto, " ");
            String comando = cmds.nextToken();

            if (comando.equalsIgnoreCase("sair"))
                break;
            else if (comando.equalsIgnoreCase("registar")) {
                try {
                    adicionaEAutenticaCliente(cmds);
                    input = "\n[" + USERNAME + "]> ";
                }
                catch (Exception e) {
                    System.out.println("[ERRO] " + e); 
                }
            }
            else if (comando.equalsIgnoreCase("autenticar")) {
                try { 
                    autenticaCliente(cmds);
                    input = "\n[" + USERNAME + "]> ";
                } 
                catch (Exception e) {
                    System.out.println("[ERRO] " + e); 
                }
            }
            else if (comando.equalsIgnoreCase("remover"))
                try { 
                    removeCliente(cmds); 
                }
                catch (Exception e) { 
                    System.out.println("[ERRO] " + e); 
                } 
            else if (comando.equalsIgnoreCase("desautenticar")) {
                try { 
                    desautenticaCliente(cmds); 
                    input = "\n> "; 
                } 
                catch (Exception e) { 
                    System.out.println("[ERRO] " + e); 
                }     
            }
            /* Os dois comandos abaixo foram substituidos pelo servico 'WatchService' executado pela thread 'DiretoriaThread' */
            /* Os dois comandos se descomentados funcionam correctamente */
            /*
            else if (comando.equalsIgnoreCase("novoficheiro")) {
                if (!adicionaFicheiroCliente(cmds))
                    System.out.println("Erro ao adicionar novo ficheiro");
            }
            else if (comando.equalsIgnoreCase("removerficheiro")) {
                if (!removeFicheiroCliente(cmds))
                    System.out.println("Erro ao remover ficheiro");
            }
            */
            else if (comando.equalsIgnoreCase("utilizadores")) {
                try { 
                    Utilizadores u = utilizadoresEFicheiros(); 
                    System.out.println("\nUtilizadores:\n" + u);
                } 
                catch (Exception e) { 
                    System.out.println("[ERRO] " + e); 
                }    
            }
            else if (comando.equalsIgnoreCase("enviar")) {
                try { 
                    enviaMensagemAoCliente(cmds); 
                } 
                catch (Exception e) { 
                    System.out.println("[ERRO] " + e);
                }    
            }
            else if (comando.equalsIgnoreCase("broadcast")) {
                try { 
                    enviaMensagemAosClientes(cmds); 
                } 
                catch (Exception e) { 
                    System.out.println("[ERRO] " + e);
                }    
            }
            /* O comando abaixo, devolve o ip e porto do cliente que fornece o ficheiro -> este metodo é usado pelo comando 'transferir'*/
            /* se descomentado funciona correctamente */
            /*
            else if (comando.equalsIgnoreCase("download")) {
                try {
                    SocketAddress u = obtemIpPortoTransferenciaFicheiros(cmds.nextToken());
                    if (u == null)
                        System.out.println("[ERRO] Ninguém possuí o ficheiro solicitado");
                    else
                        System.out.println(u);
                } catch (Exception e) {
                   System.out.println("Excepcao" + e); 
                }
            }
            */
            else if (comando.equalsIgnoreCase("transferir")) {
                 try {
                    String fich = cmds.nextToken();
                    InetSocketAddress u = obtemIpPortoTransferenciaFicheiros(fich);

                    TransferenciasCliente t = new TransferenciasCliente(fich, u);
                    threads_download.add(t);
                    //t.setDaemon(true);
                    t.start(); 
                } catch (NoSuchElementException | ClienteServidorException e) {
                    System.out.println("[ERRO] " + e);
                }
            }
            else if (comando.equalsIgnoreCase("historial")) {
                try { 
                    System.out.println("\nHistorial:\n" + obtemHistorialTransferencias()); 
                } 
                catch (ClienteServidorException e) {
                    System.out.println("[ERRO] " + e); 
                }
            }
            else if (comando.equalsIgnoreCase("help")) {
                System.out.println(getComandos());
            }
            else
                System.out.println("[ERRO] O comando '" + comando + "' não existe");

        }
        
        System.out.println("[INFO] Cliente terminado");
        Destructor();
    } 
    private boolean ligarAoServidor(final String ip_servidor, final int porto_servidor) {
        
        try {
            socket_comunicacao = new Socket(InetAddress.getByName(ip_servidor), porto_servidor);
            socket_notificacoes_tcp = new Socket(InetAddress.getByName(ip_servidor), porto_servidor);
            
            out = new ObjectOutputStream(socket_comunicacao.getOutputStream());
            in = new ObjectInputStream(socket_comunicacao.getInputStream());

            in_notificacoes = new ObjectInputStream(socket_notificacoes_tcp.getInputStream());
            thread_tcp = new ServidorNotificacoesTCP();
            //t.setDaemon(true);
            thread_tcp.start();
            
            // UDP
            socket_notificacoes_udp = new DatagramSocket();
            thread_udp = new ServidorNotificacoesUDP();
            //t.setDaemon(true);
            thread_udp.start();
        }
        catch (IOException e) {
            System.out.println("[Erro] Não é possível ligar ao servidor (" + e + ")");
            return false;
        }
        
        return true;
    }
    private boolean ligarMeuServidorTransferencias() {
        try {
            socket_transferencias = new ServerSocket(0);
            thread_upload = new TransferenciasServidor();
            //thread_upload.setDaemon(true);
            thread_upload.start();
        }
        catch (IOException e) {
            System.out.println("[Erro] Não é possível ligar o socket de transferencia de ficheiros (" + e + ")");
            return false;
        }
        
        return true;
    }
    private void Destructor() {
        
        try {
            
            if (thread_diretoria != null)
                thread_diretoria.termina();
            synchronized (threads_download) {
                for (TransferenciasCliente t: threads_download)
                    t.Destructor();
            }
            
            if (thread_upload != null)
                thread_upload.terminar();
            
            if (thread_udp != null)
                thread_udp.termina();
            
            if (thread_tcp != null)
                thread_tcp.termina();
            
            if (in != null)
                in.close();
            
            if (out != null)
                out.close();
            
            if (socket_comunicacao != null)
                socket_comunicacao.close();
        }
        catch (IOException e) {}
    }
    
    // Metodos usados pela thread 'main'
    private void adicionaEAutenticaCliente(StringTokenizer cmds) throws Exception {

        try {

            String nome = cmds.nextToken();
            String username = cmds.nextToken();
            String password = cmds.nextToken();
            String dir = cmds.nextToken();
            
            if (!new File(dir).isDirectory())
                throw new Exception("A diretoria '" + dir + "' não existe.");

            String ip = InetAddress.getLocalHost().getHostAddress();
            int tcp_transferencia_clientes = socket_transferencias.getLocalPort();
            int tcp_servidor = socket_comunicacao.getLocalPort();
            int udp = socket_notificacoes_udp.getLocalPort();
            int tcp_notificacoes_servidor = socket_notificacoes_tcp.getLocalPort();

            if (adicionarCliente(nome, username, password)) {
                autenticarCliente(username, password, ip, udp, tcp_transferencia_clientes, tcp_servidor, tcp_notificacoes_servidor);
                loginConta(username, password, dir);
                thread_diretoria = new DiretoriaThread(dir);
                thread_diretoria.start();
            }

        } catch (NoSuchElementException e) {
            throw new Exception("[ERRO] Número de parâmeros inválidos [" + e + "]");
        } catch (ClienteServidorException | UnknownHostException e) {
            throw new Exception("[ERRO] Problema ao registar-autenticar [" + e + "]");
        }
    }
    private void autenticaCliente(StringTokenizer cmds) throws Exception {

        try {

            String username = cmds.nextToken();
            String password = cmds.nextToken();
            String dir = cmds.nextToken();
            
            if (!new File(dir).isDirectory())
                throw new ClienteServidorException("A diretoria '" + dir + "' não existe.");

            String ip = InetAddress.getLocalHost().getHostAddress();
            int udp = socket_notificacoes_udp.getLocalPort();
            int tcp_transferencia_clientes = socket_transferencias.getLocalPort();
            int tcp_servidor = socket_comunicacao.getLocalPort();
            int tcp_notificacoes_servidor = socket_notificacoes_tcp.getLocalPort();

            autenticarCliente(username, password, ip, udp, tcp_transferencia_clientes, tcp_servidor, tcp_notificacoes_servidor);
            loginConta(username, password, dir);
            
            thread_diretoria = new DiretoriaThread(dir);
            thread_diretoria.start();

        } catch (NoSuchElementException e) {
            throw new Exception("Número de parâmeros inválidos [" + e + "]");
        } catch (ClienteServidorException | UnknownHostException e) {
            throw new Exception("Problema ao autenticar [" + e + "]");
        }
    }
    private void removeCliente(StringTokenizer cmds) throws Exception {
        
        try {

            String username = cmds.nextToken();
            String password = cmds.nextToken();

            removerCliente(username, password);

        } catch (NoSuchElementException e) {
            throw new Exception("Número de parâmeros inválidos [" + e + "]");
        } catch (ClienteServidorException e) {
            throw new Exception("Problema ao remover cliente [" + e + "]");
        }
        
    } 
    private void desautenticaCliente(StringTokenizer cmds) throws Exception {
        
        if (!existeConta())
            throw new Exception("Para desautenticar, fazer primeiro LOGIN");
             
        try {

            String username = USERNAME;
            String password = PASSWORD;

            desautenticarCliente(username, password);
            resetConta();
            thread_diretoria.termina();
            
        } catch (NoSuchElementException e) {
            throw new Exception("Número de parâmeros inválidos [" + e + "]");
        } catch (ClienteServidorException e) {
            throw new Exception("Problema ao desautenticar cliente [" + e + "]");
        } catch (IOException e) {
            throw new Exception("Problema ao destruir thread diretoria [" + e + "]");
        }
        
    }
    private boolean adicionaFicheiroCliente(StringTokenizer cmds) {
        
        if (!existeConta())
            return false;
        
        try {

            String username = USERNAME;
            String password = PASSWORD;
            String ficheiro = cmds.nextToken();

            adicionarFicheiroCliente(username, password, ficheiro);
            return true;
        } catch (NoSuchElementException e) {
            System.err.println("Número de parâmeros inválidos [" + e + "]");
        } catch (ClienteServidorException e) {
            System.err.println("Problema ao adicionar ficheiro ao cliente [" + e + "]");
        }
        
        return false;
    }
    private boolean removeFicheiroCliente(StringTokenizer cmds) {
        
        if (!existeConta())
            return false;
        
        try {

            String username = USERNAME;
            String password = PASSWORD;
            String ficheiro = cmds.nextToken();

            removerFicheiroCliente(username, password, ficheiro);
            return true;
        } catch (NoSuchElementException e) {
            System.err.println("Número de parâmeros inválidos [" + e + "]");
        } catch (ClienteServidorException e) {
            System.err.println("Problema ao remover ficheiro ao cliente [" + e + "]");
        }
        
        return false;
    }
    private Utilizadores utilizadoresEFicheiros() throws Exception {
        
        if (!existeConta())
            throw new Exception("Ainda não foi efetuado LOGIN");
        
        try {
            return obtemUtilizadoresEFicheiros();
        } 
        catch (ClienteServidorException e) {
            throw new Exception("Problema ao obter utilizadores e respetivos ficheiros [" + e + "]");
        }
    }
    private void enviaMensagemAoCliente(StringTokenizer cmds) throws Exception {
        
        if (!existeConta())
            throw new Exception("Ainda não foi efetuado LOGIN");
        
        try {

            String username_origem = USERNAME;
            String password = PASSWORD;
            String username_destino = cmds.nextToken();
            String titulo = cmds.nextToken();
            String descricao = cmds.nextToken();

            enviarMensagemAoCliente(username_origem, password, username_destino, titulo, descricao);
            
        } catch (NoSuchElementException e) {
            throw new Exception("Número de parâmeros inválidos [" + e + "]");
        } catch (ClienteServidorException e) {
            throw new Exception("Problema ao enviar msg ao cliente [" + e + "]");
        }

    }
    private void enviaMensagemAosClientes(StringTokenizer cmds) throws Exception {
        
        if (!existeConta())
            throw new Exception("Ainda não foi efetuado LOGIN");
        
        try {

            String username_origem = USERNAME;
            String password = PASSWORD;
            String titulo = cmds.nextToken();
            String descricao = cmds.nextToken();

            enviarMensagemATodosOsClientes(username_origem, password, titulo, descricao);
            
        } catch (NoSuchElementException e) {
            throw new Exception("Número de parâmeros inválidos [" + e + "]");
        } catch (ClienteServidorException e) {
            throw new Exception("Problema ao enviar msg ao cliente [" + e + "]");
        }
        
    }
    
    // Metodos de comunicacao com o Servidor
    @Override
    public synchronized boolean adicionarCliente(String nome, String username, String password) throws ClienteServidorException {

        int METODO = Metodos.ADICIONAR_CLIENTE;

        Mensagem msg = new Mensagem();

        msg.setMetodo(METODO);
        msg.addParametros(nome);
        msg.addParametros(username);
        msg.addParametros(password);

        try {
            out.writeUnshared(msg);
            out.flush();

            msg = (Mensagem) in.readObject();
            if (msg.getExcepcao()) {
                throw new ClienteServidorException(msg.getMsgExcepcao());
            } else {
                return (boolean) msg.getResultado();
            }

        } catch (IOException | ClassNotFoundException e) {
            throw new ClienteServidorException(e.getMessage());
        }

    }
    @Override
    public synchronized boolean removerCliente(String username, String password) throws ClienteServidorException {

        int METODO = Metodos.REMOVER_CLIENTE;

        Mensagem msg = new Mensagem();

        msg.setMetodo(METODO);
        msg.addParametros(username);
        msg.addParametros(password);

        try {
            out.writeUnshared(msg);
            out.flush();

            msg = (Mensagem) in.readObject();
            if (msg.getExcepcao()) {
                throw new ClienteServidorException(msg.getMsgExcepcao());
            } else {
                return (boolean) msg.getResultado();
            }

        } catch (IOException | ClassNotFoundException e) {
            throw new ClienteServidorException(e.getMessage());
        }
    }
    @Override
    public synchronized boolean autenticarCliente(String username, String password, String ip, int udp, int tcp_clientes, int tcp_transf_clientes, int tcp_notificacoes_servidor) throws ClienteServidorException {

        int METODO = Metodos.AUTENTICAR_CLIENTE;

        Mensagem msg = new Mensagem();

        msg.setMetodo(METODO);
        msg.addParametros(username);
        msg.addParametros(password);
        msg.addParametros(ip);
        msg.addParametros(String.valueOf(udp));
        msg.addParametros(String.valueOf(tcp_clientes));
        msg.addParametros(String.valueOf(tcp_transf_clientes));
        msg.addParametros(String.valueOf(tcp_notificacoes_servidor));

        try {
            out.writeUnshared(msg);
            out.flush();

            msg = (Mensagem) in.readObject();
            if (msg.getExcepcao()) {
                throw new ClienteServidorException(msg.getMsgExcepcao());
            } else {
                return (boolean) msg.getResultado();
            }

        } catch (IOException | ClassNotFoundException e) {
            throw new ClienteServidorException(e.getMessage());
        }
    }
    @Override
    public synchronized boolean desautenticarCliente(String username, String password) throws ClienteServidorException {

        int METODO = Metodos.DESAUTENTICAR_CLIENTE;

        Mensagem msg = new Mensagem();

        msg.setMetodo(METODO);
        msg.addParametros(username);
        msg.addParametros(password);

        try {
            out.writeUnshared(msg);
            out.flush();

            msg = (Mensagem) in.readObject();
            if (msg.getExcepcao()) {
                throw new ClienteServidorException(msg.getMsgExcepcao());
            } else {
                return (boolean) msg.getResultado();
            }

        } catch (IOException | ClassNotFoundException e) {
            throw new ClienteServidorException(e.getMessage());
        }
    }
    @Override
    public synchronized boolean adicionarFicheiroCliente(String username, String password, String nome_ficheiro) throws ClienteServidorException {
        
       int METODO = Metodos.ADICIONAR_FICHEIRO_CLIENTE;

        Mensagem msg = new Mensagem();

        msg.setMetodo(METODO);
        msg.addParametros(username);
        msg.addParametros(password);
        msg.addParametros(nome_ficheiro);

        try {
            out.writeUnshared(msg);
            out.flush();

            msg = (Mensagem) in.readObject();
            if (msg.getExcepcao()) {
                throw new ClienteServidorException(msg.getMsgExcepcao());
            } else {
                return (boolean) msg.getResultado();
            }

        } catch (IOException | ClassNotFoundException e) {
            throw new ClienteServidorException(e.getMessage());
        } 
        
    }
    @Override
    public synchronized boolean removerFicheiroCliente(String username, String password, String nome_ficheiro) throws ClienteServidorException {
        
        int METODO = Metodos.REMOVER_FICHEIRO_CLIENTE;

        Mensagem msg = new Mensagem();

        msg.setMetodo(METODO);
        msg.addParametros(username);
        msg.addParametros(password);
        msg.addParametros(nome_ficheiro);

        try {
            out.writeUnshared(msg);
            out.flush();

            msg = (Mensagem) in.readObject();
            if (msg.getExcepcao()) {
                throw new ClienteServidorException(msg.getMsgExcepcao());
            } else {
                return (boolean) msg.getResultado();
            }

        } catch (IOException | ClassNotFoundException e) {
            throw new ClienteServidorException(e.getMessage());
        } 
        
    }
    @Override
    public synchronized Utilizadores obtemUtilizadoresEFicheiros() throws ClienteServidorException {
        
        int METODO = Metodos.OBTEM_UTILIZADORES_E_FICHEIROS;

        Mensagem msg = new Mensagem();

        msg.setMetodo(METODO);

        try {
            out.writeUnshared(msg);
            out.flush();

            msg = (Mensagem) in.readObject();

            if (msg.getExcepcao()) {
                throw new ClienteServidorException(msg.getMsgExcepcao());
            } else {
                return (Utilizadores) msg.getResultado();
            }

        } catch (IOException | ClassNotFoundException e) {
            throw new ClienteServidorException(e.getMessage());
        } 
        
        
    }
    @Override
    public synchronized boolean enviarMensagemAoCliente(String username_origem, String password, String username_destino, String titulo, String descricao) throws ClienteServidorException {
        
        int METODO = Metodos.ENVIAR_MSG_A_CLIENTE;

        Mensagem msg = new Mensagem();

        msg.setMetodo(METODO);
        msg.addParametros(username_origem);
        msg.addParametros(password);
        msg.addParametros(username_destino);
        msg.addParametros(titulo);
        msg.addParametros(descricao);

        try {
            out.writeUnshared(msg);
            out.flush();

            msg = (Mensagem) in.readObject();
            if (msg.getExcepcao()) {
                throw new ClienteServidorException(msg.getMsgExcepcao());
            } else {
                return (boolean) msg.getResultado();
            }

        } catch (IOException | ClassNotFoundException e) {
            throw new ClienteServidorException(e.getMessage());
        } 
        
    }
    @Override
    public synchronized boolean enviarMensagemATodosOsClientes(String username_origem, String password, String titulo, String descricao) throws ClienteServidorException {
        
        int METODO = Metodos.ENVIAR_MSG_AOS_CLIENTES;

        Mensagem msg = new Mensagem();

        msg.setMetodo(METODO);
        msg.addParametros(username_origem);
        msg.addParametros(password);
        msg.addParametros(titulo);
        msg.addParametros(descricao);

        try {
            out.writeUnshared(msg);
            out.flush();

            msg = (Mensagem) in.readObject();
            if (msg.getExcepcao()) {
                throw new ClienteServidorException(msg.getMsgExcepcao());
            } else {
                return (boolean) msg.getResultado();
            }

        } catch (IOException | ClassNotFoundException e) {
            throw new ClienteServidorException(e.getMessage());
        } 
        
    }
    @Override
    public synchronized InetSocketAddress obtemIpPortoTransferenciaFicheiros(String nome_ficheiro) throws ClienteServidorException {
        
        int METODO = Metodos.OBTEM_IP_PORTO_TRANSFERENCIA;

        Mensagem msg = new Mensagem();

        msg.setMetodo(METODO);
        msg.addParametros(nome_ficheiro);

        try {
            out.writeUnshared(msg);
            out.flush();

            msg = (Mensagem) in.readObject();
            if (msg.getExcepcao()) {
                throw new ClienteServidorException(msg.getMsgExcepcao());
            } else {
                return (InetSocketAddress) msg.getResultado();
            }

        } catch (IOException | ClassNotFoundException e) {
            throw new ClienteServidorException(e.getMessage());
        } 
        
    }
    @Override
    public synchronized String obtemHistorialTransferencias() throws ClienteServidorException {
        
        int METODO = Metodos.OBTEM_HISTORIAL_TRANSFERENCIAS;

        Mensagem msg = new Mensagem();

        msg.setMetodo(METODO);

        try {
            out.writeUnshared(msg);
            out.flush();

            msg = (Mensagem) in.readObject();
            if (msg.getExcepcao()) {
                throw new ClienteServidorException(msg.getMsgExcepcao());
            } else {
                return (String) msg.getResultado();
            }

        } catch (IOException | ClassNotFoundException e) {
            throw new ClienteServidorException(e.getMessage());
        } 
        
    }
    @Override
    public synchronized boolean adicionaTransferenciaNoHistorial(Transferencia t) throws ClienteServidorException {

        int METODO = Metodos.ADICIONA_TRANSFERENCIA_AO_HISTORIAL;

        Mensagem msg = new Mensagem();

        msg.setMetodo(METODO);
        msg.addParametros(t.origem);
        msg.addParametros(t.destino);
        msg.addParametros(t.ficheiro);
        msg.addParametros(t.tipo);
        msg.addParametros(t.ocorrencia.toString());

        try {
            out.writeUnshared(msg);
            out.flush();


            msg = (Mensagem) in.readObject();

            if (msg.getExcepcao()) {
                throw new ClienteServidorException(msg.getMsgExcepcao());
            } else {
                return (boolean) msg.getResultado();
            }

        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            throw new ClienteServidorException(e.getMessage());
        } 
        
    }
    @Override
    public synchronized boolean existeFicheiro(String nome_ficheiro) throws ClienteServidorException {
        
        int METODO = Metodos.EXISTE_FICHEIRO;

        Mensagem msg = new Mensagem();

        msg.setMetodo(METODO);
        msg.addParametros(nome_ficheiro);

        try {
            out.writeUnshared(msg);
            out.flush();

            msg = (Mensagem) in.readObject();
            if (msg.getExcepcao()) {
                throw new ClienteServidorException(msg.getMsgExcepcao());
            } else {
                return (boolean) msg.getResultado();
            }

        } catch (IOException | ClassNotFoundException e) {
            throw new ClienteServidorException(e.getMessage());
        } 
        
    }
    @Override
    public synchronized boolean autenticadoUtilizador(String nome) {
        int METODO = Metodos.EXISTE_FICHEIRO;

        Mensagem msg = new Mensagem();

        msg.setMetodo(METODO);
        msg.addParametros(nome);

        try {
            out.writeUnshared(msg);
            out.flush();

            msg = (Mensagem) in.readObject();
            return (boolean) msg.getResultado();

        } catch (IOException | ClassNotFoundException e) {
            return false;
        } 
        
    }

    
    private class ServidorNotificacoesTCP extends Thread {
        
        private boolean termina = false;
        @Override
        public void run() {
            
            try {
                while (!termina) {
                    
                    String msg = (String) in_notificacoes.readObject();
                    
                    System.out.println("\nThread TCP: " + msg);
                }
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("\nThread Notificacoes TCP terminada à força: " + e);
            }
        }
        
        public void termina() throws IOException {
            termina = true;
            in_notificacoes.close();
        }
    }
    private class ServidorNotificacoesUDP extends Thread {
        
        private boolean terminar = false;
        @Override
        public void run() {
            try {
                while (!terminar) {
                    DatagramPacket pacote = new DatagramPacket(new byte[4096], 4096);
                    socket_notificacoes_udp.receive(pacote);
                    
                    socket_notificacoes_udp.send(new DatagramPacket("ok".getBytes(), "ok".getBytes().length, pacote.getAddress(), pacote.getPort()));
                    String msg = new String(pacote.getData(), 0, pacote.getLength());
                    
                    // Retirar condicao if para observar output do keep alive recebido pelo cliente
                    if (!msg.startsWith("Keep Alive")) {
                        System.out.println("\nThread UDP: " + msg);
                    }
                    
                }
            } catch (IOException e) {
                System.err.println("\nThread Notificacoes UDP terminada à força: " + e);
            }
        }
        
        public void termina() {
            terminar = true;
            socket_notificacoes_udp.close();
        }
    }
    
    
    private class TransferenciasServidor extends Thread {
        
        private class TransferenciaThread extends Thread {
            
            private final Socket s;
            private ObjectInputStream oin = null;
            private ObjectOutputStream oos = null;
            private FileInputStream fin = null;
            
            public TransferenciaThread(final Socket s) throws IOException {
                this.s = s;
                this.oin = new ObjectInputStream(s.getInputStream());
                this.oos = new ObjectOutputStream(s.getOutputStream());
            }
            
            @Override
            public void run() {
                
                String ficheiro;
                String username_destino;
                String caminho_com_ficheiro;
                
                try {
                    
                    ficheiro = (String) oin.readObject();
                    username_destino = (String) oin.readObject();
                    oos.writeObject(USERNAME);
                    oos.flush();
                    
                    caminho_com_ficheiro = new File(DIRETORIA + File.separator + ficheiro).getCanonicalPath();
                    
                    if (!new File(caminho_com_ficheiro).exists()) {
                        System.out.println("[ERRO] Thread " + getId() + ": O ficheiro '" + ficheiro + "' não existe");
                        Destructor();
                        return;
                    }
                    
                    System.out.println("[INFO] Thread " + getId() + ": A transferir o ficheiro '" + ficheiro + "'");
                    fin = new FileInputStream(caminho_com_ficheiro);
                    byte bytes[] = new byte[4096];
                    int cnt;
                    while ((cnt = fin.read(bytes)) > 0) {
                        oos.write(bytes, 0, cnt);
                        oos.flush();
                    }
                    
                    Destructor();

                    try {
                        Transferencia t = new Transferencia(USERNAME, username_destino, ficheiro, "Upload", new java.sql.Timestamp(Calendar.getInstance().getTimeInMillis()));
                        Cliente.this.adicionaTransferenciaNoHistorial(t);
                    }
                    catch (ClienteServidorException e) {
                        System.out.println("[INFO] Thread " + getId() + ": Erro ao guardar historial "+ e);
                    }
                    
                    System.out.println("[INFO] Thread " + getId() + ": Transferência concluída do ficheiro '" + ficheiro + "'");
                } 
                catch (IOException | ClassNotFoundException ex) {
                    Destructor();
                    System.err.println("[ERRO] Thread Servidor-Transferencia " + getId() + ": Terminada à força " + ex);
                }

            }  
            
            private void Destructor() {
                try { if (fin != null) fin.close(); } catch (IOException e) {}
                try { if (oos != null) oos.close(); } catch (IOException e) {}
                try { if (oin != null) oin.close(); } catch (IOException e) {}
                try { if (s != null) s.close(); } catch (IOException e) {}
            }
        }
        
        private final ArrayList<TransferenciaThread> threads_serv = new ArrayList<>(); // Threads de upload (cada thread e responsavel por tratar do upload de um ficheiro)
        private boolean terminar = false;
        @Override
        public void run() {

            try {
                
                while (!terminar) {
                    Socket s = socket_transferencias.accept();
                    TransferenciaThread t = null;
                    try {
                        t = new TransferenciaThread(s);
                        threads_serv.add(t);
                        t.setDaemon(true);
                        t.start();
                    } 
                    catch (IOException e) {
                        if (t != null)
                            t.Destructor();
                        System.err.println("\nThread Servidor " + getId() + ": Erro ao atender cliente " + e);
                    }
                }
                
            } 
            catch (IOException ex) {
                System.err.println("\nThread Servidor " + getId() + ": Terminada à força " + ex);
            }
            
        }
        
        public void terminar() {
            Iterator<TransferenciaThread> it = threads_serv.iterator();
            while (it.hasNext()) {
                it.next().Destructor();
            }
            terminar = true;
            try { socket_transferencias.close(); } catch (IOException e) {}
        }
    }
    private class TransferenciasCliente extends Thread {
        
        private final String ficheiro;
        private final InetSocketAddress servidor;
        
        private Socket s;
        private FileOutputStream fout;
        private ObjectOutputStream oos;
        private ObjectInputStream oin;
        
        public TransferenciasCliente(String ficheiro, InetSocketAddress servidor) {
            this.ficheiro = ficheiro;
            this.servidor = servidor;
            this.s = null;
            this.fout = null;
            this.oos = null;
            this.oin = null;
        }
        
        private void Destructor() {
            try { if (fout != null) fout.close(); } catch (IOException e) {}
            /*
            try { if (oin != null) oin.close(); } catch (IOException e) {}
            try { if (oos != null) oos.close(); } catch (IOException e) {}
            try { if (s != null) s.close(); } catch (IOException e) {}
            */
        }
        @Override
        public void run() {
            
            String caminho_com_ficheiro;
            String username_destino;       
            
            try {
                
                caminho_com_ficheiro = new File(DIRETORIA + File.separator + ficheiro).getCanonicalPath();
                
                if (new File(caminho_com_ficheiro).exists()) {
                    System.out.println("\n[ERRO] Thread " + getId() + ": Um ficheiro com o nome '" + ficheiro + "' já existe na diretoria.");
                    return;
                }
                
                System.out.println("\n[INFO] Thread " + getId() + ": A Transferir ficheiro '" + ficheiro + "'...");
                
                s = new Socket(servidor.getAddress(), servidor.getPort());
                oos = new ObjectOutputStream(s.getOutputStream());
                oin = new ObjectInputStream(s.getInputStream());
                
                oos.writeObject(ficheiro);
                oos.flush();
                oos.writeObject(USERNAME);
                oos.flush();
                
                username_destino = (String) oin.readObject();
                
                fout = new FileOutputStream(caminho_com_ficheiro);
                byte bytes[] = new byte[4096];
                int cnt;
                
                try {
                    while ((cnt = oin.read(bytes)) > 0)
                        fout.write(bytes, 0, cnt);
                }
                catch (IOException e) {
                    Destructor();
                    new File(new File(DIRETORIA + File.separator + ficheiro).getCanonicalPath()).delete();
                    System.err.println("\n[INFO] Thread " + getId() + ": Transferência incompleta, o ficheiro '" + ficheiro + "' foi apagado: " + e);
                    return;
                }
                
                Destructor();
 
                try {
                    Transferencia t = new Transferencia(USERNAME, username_destino, ficheiro, "Download", new java.sql.Timestamp(Calendar.getInstance().getTimeInMillis()));
                    Cliente.this.adicionaTransferenciaNoHistorial(t);
                }
                catch (ClienteServidorException e) {
                    System.out.println("\n[INFO] Thread " + getId() + ": Erro ao guardar historial "+ e);
                }
                
                System.out.println("\n[INFO] Thread " + getId() + ": Transferência do ficheiro '" + ficheiro + "' concluída com SUCESSO!");
            } 
            catch (ClassNotFoundException | IOException ex) {
                
                Destructor();
                System.err.println("[INFO] Thread " + getId() + ": Terminada à força (fecho do socket)" + ex);
            }
            
        }
    }
    
    private class DiretoriaThread extends Thread {
        
        private boolean terminar;
        private WatchService ws;
        private final String diretoria;
        
        public DiretoriaThread(String diretoria) {
            this.diretoria = diretoria;
            ws = null;
            terminar = false;
        }
        
        @Override
        public void run() {
            
            try {
                ws = FileSystems.getDefault().newWatchService();
                Path pTemp = Paths.get(diretoria);

                pTemp.register(ws, new WatchEvent.Kind[]{ENTRY_CREATE, ENTRY_DELETE});

                while (!terminar) {
                    
                    WatchKey k = ws.take();
                    
                    for (WatchEvent<?> e : k.pollEvents()) {
                        
                        String fich = e.context().toString();
                        
                        try {
                        
                            if (fich.contains(".")) {
                                if (!fich.equals(".DS_Store") && e.kind() == ENTRY_CREATE 
                                        && USERNAME != null && !Cliente.this.existeFicheiro(fich)) {

                                    Cliente.this.adicionarFicheiroCliente(USERNAME, PASSWORD, fich);
                                    System.out.printf("%s %d %s\n", e.kind(), e.count(), fich);
                                    System.out.flush();
                                }
                                else if (!fich.equals(".DS_Store") && e.kind() == ENTRY_DELETE 
                                        && USERNAME != null && Cliente.this.existeFicheiro(fich)) {
                                    Cliente.this.removerFicheiroCliente(USERNAME, PASSWORD, fich);
                                    System.out.printf("%s %d %s\n", e.kind(), e.count(), fich);
                                    System.out.flush();
                                }
                            }
                        
                        }
                        catch (ClienteServidorException ex) {
                            //System.err.println("[ERRO] Problema ao adicionar/apagar ficheiro - Thread Diretoria (" + ex +")");
                        }
                        
                    }
                    
                    k.reset();
                }
            } catch (IOException | InterruptedException | ClosedWatchServiceException e) {            
                System.err.println("Thread Diretoria terminada à força: " + e);
            }
            
        }
        
        public synchronized void termina() throws IOException {
            ws.close();
            terminar = true;
        }
    }
}

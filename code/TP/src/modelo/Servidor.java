
package modelo;

import java.io.*;
import java.net.*;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import modelo.comunicacao.*;
import modelo.comunicacaormi.IListener;
import modelo.comunicacaormi.ISensor;


public class Servidor implements ComunicacaoClienteServidor {
    
    private final static int N_FALHAS = 3;
    private final static int TIMEOUT_UDP = 100; // segundos
    private final static int TIMEOUT_KEEP_ALIVE = 10 * 1000; // 10 segundos
    private final ArrayList<ClienteThread> clientes; // todos os clientes ligados a este servidor
    
    private ServerSocket socket_servidor;
    private DatagramSocket socket_notificacoes_udp, keep_alive_socket;
    private KeepAliveThread thread;
    
    // Base de Dados
    private String ip_bd = "127.0.0.1"; // IP da Base de Dados MySQL
    private final String username_bd = "tiago";
    private final String password_bd = "4242";
    private Statement stmt;
    
    // RMI
    private String ip_rmi = "127.0.0.1";
    private static final String URL_RMI = "ServicoRMI";
    private Registry registry;
    private final ArrayList<IListener> listeners;
    private ISensor sensor;
    
    public Servidor() {
        clientes = new ArrayList<>();
        listeners = new ArrayList<>();
        socket_servidor = null;
        socket_notificacoes_udp = null;
        stmt = null;
    }
    public void iniciar() {
        
        Scanner sc = new Scanner(System.in);
        
        System.out.println("------------ Servidor ------------\n");
        System.out.println("IP da Base de Dados:");
        System.out.print("> ");
        ip_bd = sc.nextLine();
        
        System.out.println("IP do RMI:");
        System.out.print("> ");
        ip_rmi = sc.nextLine();
        
        if (!estabeleceLigacaoRMI())
            return;
        
        if (!estabeleceLigacaoABaseDados())
            return;
        
        if (!iniciaSockets())
            return;

        System.out.println("\n------------ Servidor [" + socket_servidor.getLocalPort() + "] ------------\n");
        System.out.println("À espera de clientes...");        
        
        aguardaClientes();
    }
    
    // Metodos do metodo iniciar
    private boolean iniciaSockets() {
        
        try {
            socket_servidor = new ServerSocket(0);  
            socket_notificacoes_udp = new DatagramSocket(0);    
            socket_notificacoes_udp.setSoTimeout(TIMEOUT_UDP);
            
            thread = new KeepAliveThread();
            thread.start();
        }
        catch (IOException e) {
            System.out.println("[Erro] Um dos sockets encontra-se já em utilização (" + e + ")");        
            System.out.println("[Info] A terminar programa.");  
            return false;
        }
        return true;
    }  
    private boolean estabeleceLigacaoABaseDados() {
        
        try {
            stmt = obtemNovoStatement();
        }
        catch (ClassNotFoundException | SQLException | NullPointerException e) {
            System.out.println("[Erro] Não é possível ligar à base de dados (" + e + ")");        
            System.out.println("[Info] A terminar programa.");  
            return false;
        }
        return true;
    }
    private boolean estabeleceLigacaoRMI() {
        
        try {
        
            String ip_local = InetAddress.getLocalHost().getHostAddress();
            System.out.println("\nIP deste servidor: " + ip_local);
            
            if (!ip_rmi.equals(ip_local))
                throw new RemoteException("RMI já ligado noutro computador");
            
            registry = LocateRegistry.createRegistry(1099);
            sensor = new Sensor();
            registry.bind(URL_RMI, sensor);
            
            System.out.println("RMI Criado neste computador: " + ip_rmi);
        }
        catch (AlreadyBoundException | RemoteException | UnknownHostException e) {
            
            if (e instanceof RemoteException) {
                try { 

                    System.out.println("A obter um RMI existente no computador: " + ip_rmi);
                    registry = LocateRegistry.getRegistry(ip_rmi, 1099);
                    sensor = (ISensor)registry.lookup(URL_RMI);

                    return true;
                } 
                catch (NotBoundException | RemoteException ex) {}
            }
            
            System.out.println("[Erro] Não é possível iniciar o RMI (" + e + ")");
            System.out.println("[Info] A terminar programa.");  
            return false;        

        }
        
        return true;
    }
    private Statement obtemNovoStatement() throws ClassNotFoundException, SQLException {
        Class.forName("com.mysql.cj.jdbc.Driver");
        return DriverManager.getConnection("jdbc:mysql://" + ip_bd + "/trabalho_pratico", username_bd, password_bd)
                    .createStatement();
    }
    private void aguardaClientes() {
        
        while (true) {
            
            try {
                
                Socket cli_metodos = socket_servidor.accept(); 
                Socket cli_notificacoes_tcp = socket_servidor.accept();
                
                ClienteThread t = new ClienteThread(cli_metodos, cli_notificacoes_tcp);
                clientes.add(t);
                t.start();
                
                System.out.println("[Info] Novo cliente ligado via TCP: " + t.getName());
                System.out.println("[Info] Clientes existentes = " + clientes.size() + ": " + clientes + "\n");
            } 
            catch (IOException e) {
                System.out.println("[Erro] Problema na ligação TCP com um dos clientes (" + e + ")");
            }
            
        } // Fim do ciclo WHILE
        
        // thread.termina();
    }
    
    
    // Métodos de gestao de falhas UDP (KEEPALIVE)
    public int obtemNumeroFalhasDoCliente(String username) throws SQLException, ClienteServidorException {
        
        ResultSet rs = null;
        int falhas = -1;
        
        try {
            rs = stmt.executeQuery("SELECT falhas FROM Utilizadores WHERE username = '" + username + "' AND autenticado = TRUE;");
            
            if (!rs.next())
                throw new ClienteServidorException("O utilizador '" + username + "' NAO existe ou NAO esta autenticado");
                
            falhas = rs.getInt("falhas");
        }
        catch (SQLException | ClienteServidorException e) {
            throw e;
        }
        finally {
            if (rs != null)
                rs.close();
        }
        
        return falhas;
    }
    public int incrementaFalhasDoCliente(String username) throws SQLException, ClienteServidorException {
        
        ResultSet rs = null;
        int falhas, id;
        
        try {
            rs = stmt.executeQuery("SELECT falhas , utilizador_id FROM Utilizadores WHERE username = '" + username + "' AND autenticado = TRUE;");
            
            if (!rs.next())
                throw new ClienteServidorException("O utilizador '" + username + "' NAO existe ou NAO esta autenticado");
                
            id = rs.getInt("utilizador_id");
            falhas = rs.getInt("falhas");
            
            stmt.executeUpdate("UPDATE Utilizadores SET falhas = " + (falhas + 1) + " WHERE utilizador_id = " + id + ";");
        }
        catch (SQLException | ClienteServidorException e) {
            throw new ClienteServidorException("-> " + e.toString());
        }
        finally {
            if (rs != null)
                rs.close();
        }
        
        return falhas;
    }  
    

    // Métodos de Comunicacao com o cliente
    @Override
    public boolean adicionarCliente(String nome, String username, String password) throws ClienteServidorException {
        try {
            stmt.executeUpdate("INSERT INTO Utilizadores(nome, username, palavra_passe, autenticado, falhas)"
                    + "values('" + nome + "', '" + username + "', '" + password + "', FALSE, 0);");
        } catch (SQLException e) {
            throw new ClienteServidorException(e.toString());
        }
        
        return true;
    }
    @Override
    public boolean removerCliente(String username, String password) throws ClienteServidorException {
        try {
            ResultSet rs = stmt.executeQuery("SELECT utilizador_id, palavra_passe, autenticado FROM Utilizadores WHERE username = '"
                    + username + "';");
            
            if (!rs.next())
                throw new ClienteServidorException("O utilizador '" + username + "' NAO existe");
            
            int id = rs.getInt("utilizador_id");
            boolean autenticado = rs.getBoolean("autenticado");
            String palavra_passe = rs.getString("palavra_passe");
            
            if (!palavra_passe.equals(password))
                throw new ClienteServidorException("Palavra-passe errada");
            if (autenticado)
                throw new ClienteServidorException("O utilizador '" + username + "' encontra-se autenticado");
            
            stmt.executeUpdate("DELETE FROM Utilizadores WHERE utilizador_id = " + id +";");

        } catch (SQLException e) {
            throw new ClienteServidorException(e.toString());
        }
        
        return true;
    }
    @Override
    public boolean autenticarCliente(String username, String password, String ip, int udp, int tcp_transf_clientes, int tcp_servidor, int tcp_notificacoes_servidor) throws ClienteServidorException {
        try {
            ResultSet rs = stmt.executeQuery("SELECT utilizador_id, palavra_passe, autenticado FROM Utilizadores WHERE username = '"
                    + username + "';");
            
            if (!rs.next())
                throw new ClienteServidorException("O utilizador '" + username + "' NAO existe");
            
            int id = rs.getInt("utilizador_id");
            boolean autenticado = rs.getBoolean("autenticado");
            String palavra_passe = rs.getString("palavra_passe");
            
            if (!palavra_passe.equals(password))
                throw new ClienteServidorException("Palavra-passe errada");
            if (autenticado)
                throw new ClienteServidorException("O utilizador '" + username + "' ja se encontra autenticado");
            
            stmt.executeUpdate("UPDATE utilizadores SET autenticado = TRUE, ip = '" + ip 
                    + "', udp = " + udp + ", tcp_clientes = " + tcp_transf_clientes + ", tcp_servidor = " + tcp_servidor
                    + ", tcp_notificacoes_servidor = " + tcp_notificacoes_servidor + " WHERE utilizador_id = " + id + ";");

        } catch (SQLException e) {
            throw new ClienteServidorException(e.toString());
        }
        
        return true;
    }
    @Override
    public boolean desautenticarCliente(String username, String password) throws ClienteServidorException {
        try {
            ResultSet rs = stmt.executeQuery("SELECT utilizador_id, palavra_passe, autenticado, falhas FROM Utilizadores where username = '"
                    + username + "';");
            if (!rs.next())
                throw new ClienteServidorException("O utilizador '" + username + "' NAO existe");
            
            int id = rs.getInt("utilizador_id");
            boolean autenticado = rs.getBoolean("autenticado");
            String palavra_passe = rs.getString("palavra_passe");
            int falhas = rs.getInt("falhas");
            
            if (!palavra_passe.equals(password))
                throw new ClienteServidorException("Palavra-passe errada");
            if (!autenticado)
                throw new ClienteServidorException("O utilizador '" + username + "' ja se encontra desconectado");
            
            stmt.executeUpdate("UPDATE Utilizadores SET autenticado = FALSE, falhas = 0 WHERE utilizador_id = " + id + ";");
            // Apagar ficheiros do cliente
            stmt.executeUpdate("DELETE FROM Ficheiros WHERE utilizador_id = " + id + ";");

        } catch (SQLException e) {
            throw new ClienteServidorException(e.toString());
        }
        
        return true;
    }
    @Override
    public boolean adicionarFicheiroCliente(String username, String password, String nome_ficheiro) throws ClienteServidorException {
        try {
            ResultSet rs = stmt.executeQuery("SELECT utilizador_id, autenticado, palavra_passe FROM Utilizadores WHERE username = '"
                    + username + "';");
            
            if (!rs.next())
                throw new ClienteServidorException("O utilizador '" + username + "' NAO existe");
            
            int id = rs.getInt("utilizador_id");
            boolean autenticado = rs.getBoolean("autenticado");
            String pass = rs.getString("palavra_passe");
            
            if (!pass.equalsIgnoreCase(password))
                throw new ClienteServidorException("Palavra passe '" + password + "' INVALIDA");
            
            if (!autenticado)
                throw new ClienteServidorException("O utilizador '" + username + "' NAO esta autenticado");
            
            
            rs = stmt.executeQuery("SELECT nome FROM ficheiros where nome = '"
                    + nome_ficheiro + "';");
            if (rs.next())
                throw new ClienteServidorException("O ficheiro '" + nome_ficheiro + "' já existe");
            
            stmt.executeUpdate("INSERT INTO ficheiros (nome, utilizador_id) values ('" + nome_ficheiro + "', " + id + ");");
        } catch (SQLException e) {
            throw new ClienteServidorException(e.toString());
        }
        
        return true;
    }
    @Override
    public boolean removerFicheiroCliente(String username, String password, String nome_ficheiro) throws ClienteServidorException {
        try {
            ResultSet rs = stmt.executeQuery("SELECT utilizador_id, palavra_passe, autenticado FROM Utilizadores WHERE username = '"
                    + username + "';");
            
            if (!rs.next())
                throw new ClienteServidorException("O utilizador '" + username + "' NAO existe");
            
            int id = rs.getInt("utilizador_id");
            boolean autenticado = rs.getBoolean("autenticado");
            String pass = rs.getString("palavra_passe");
            
            if (!pass.equalsIgnoreCase(password))
                throw new ClienteServidorException("Palavra passe '" + password + "' INVALIDA");
            
            if (!autenticado)
                throw new ClienteServidorException("O utilizador '" + username + "' NAO esta autenticado");
            
            
            rs = stmt.executeQuery("SELECT nome, utilizador_id, ficheiro_id FROM Ficheiros WHERE nome = '"
                    + nome_ficheiro + "';");
            if (!rs.next())
                throw new ClienteServidorException("O ficheiro '" + nome_ficheiro + "' NAO existe");
            
            int id_utilizador_tabela_ficheiros = rs.getInt("utilizador_id");
            int ficheiro_id = rs.getInt("ficheiro_id");
            String nome = rs.getString("nome");
            if (id_utilizador_tabela_ficheiros == id && nome.equals(nome_ficheiro))
                stmt.executeUpdate("DELETE FROM Ficheiros WHERE ficheiro_id = " + ficheiro_id + ";");
            else
                throw new ClienteServidorException("O ficheiro '" + nome_ficheiro + "' nao pertence a este utilizador");

        } catch (SQLException e) {
            throw new ClienteServidorException(e.toString());
        }
        
        return true;
    }
    @Override
    public Utilizadores obtemUtilizadoresEFicheiros() throws ClienteServidorException {
        
        Utilizadores u = new Utilizadores();
        try {
            ResultSet utilizadores = DriverManager.getConnection("jdbc:mysql://" + ip_bd + "/trabalho_pratico", username_bd, password_bd).createStatement().executeQuery("select utilizador_id, username from utilizadores WHERE autenticado = TRUE;");
            
            while (utilizadores.next()) {
                
                int id = utilizadores.getInt("utilizador_id");
                String username = utilizadores.getString("username");
                
                Statement stmt_temp = DriverManager.getConnection("jdbc:mysql://" + ip_bd + "/trabalho_pratico", username_bd, password_bd).createStatement();
        
                ResultSet ficheiros = stmt_temp.executeQuery("select nome from ficheiros WHERE utilizador_id = " + id + ";");
                u.adicionaUtilizador(username);
                while (ficheiros.next()) {
                    String ficheiro = ficheiros.getString("nome");
                    u.adicionaFicheiroAoUtilizador(username, ficheiro);
                }
            }
        } catch (SQLException e) {
            System.err.println("Sera que isto vai acabar? -> " + e);
            throw new ClienteServidorException(e.toString());
        }
        
        return u;
    }
    @Override
    public boolean enviarMensagemAoCliente(String username_origem, String password, String username_destino, String titulo, String descricao) throws ClienteServidorException {
        
        try {
            
            String mensagem = "[Mensagem '" + titulo + "': " + username_origem + "] = '" + descricao + "'";
            ResultSet rs = stmt.executeQuery("SELECT autenticado, ip, palavra_passe, tcp_notificacoes_servidor, udp FROM Utilizadores WHERE username = '" + username_destino + "';");
            
            if (!rs.next())
                throw new ClienteServidorException("O utilizador '" + username_destino + "' NAO existe ou NAO esta autenticado");
                
            boolean autenticado = rs.getBoolean("autenticado");
            String ip = rs.getString("ip");
            
            if (!autenticado)
                throw new ClienteServidorException("O utilizador '" + username_destino + "' NAO esta autenticado");
            
            // Verifica se o [Cliente de Destino] se se encontra registado neste servidor (ENVIAR POR TCP)
            Iterator<ClienteThread> it = clientes.iterator();
            while (it.hasNext()) {
                ClienteThread c = it.next();
                if (c.USERNAME.equals(username_destino)) {
                    c.out_notificacoes.writeObject(mensagem);
                    c.out_notificacoes.flush();
                    break;
                }
            }
            
            // O [Cliente de Destino] não se encontra registado neste servidor (ENVIAR POR UDP)
            int porto_udp = rs.getInt("udp");
            String palavra_passe = rs.getString("palavra_passe");
            
            DatagramPacket pacote = new DatagramPacket(mensagem.getBytes(), mensagem.getBytes().length, InetAddress.getByName(ip), porto_udp);
            socket_notificacoes_udp.send(pacote);
            
            try { 
                socket_notificacoes_udp.receive(new DatagramPacket(new byte[1024], 1024));
            } 
            catch (SocketTimeoutException e) {
                incrementaFalhasDoCliente(username_destino);
                if (obtemNumeroFalhasDoCliente(username_destino) >= N_FALHAS)
                    this.desautenticarCliente(username_destino, palavra_passe);
            }
                
            return true;
        } catch (SQLException | IOException e) {
            throw new ClienteServidorException(e.getMessage());
        }
    }
    @Override
    public boolean enviarMensagemATodosOsClientes(String username_origem, String password, String titulo, String descricao) throws ClienteServidorException {
        
        try {
            
            String mensagem = "[Mensagem '" + titulo + "': " + username_origem + "] = '" + descricao + "'";
            ResultSet rs = stmt.executeQuery("SELECT username, ip, tcp_notificacoes_servidor, udp FROM Utilizadores WHERE autenticado = TRUE;");
            
            while (rs.next()) {
                
                String username = rs.getString("username");
                String ip = rs.getString("ip");
                int udp = rs.getInt("udp");
                
                for (int i = 0; i < clientes.size(); i++) {
                    if (username.equals(clientes.get(i).USERNAME)) {
                        clientes.get(i).getOutNotificacoes().writeObject(mensagem);
                        clientes.get(i).getOutNotificacoes().flush();
                        break;
                    }
                }
            }

            rs.close();
            rs = obtemNovoStatement().executeQuery("SELECT username, ip, palavra_passe, tcp_notificacoes_servidor, udp FROM Utilizadores WHERE autenticado = TRUE;");
            
            ciclo_externo: while (rs.next()) {
                
                String username = rs.getString("username");
                String ip = rs.getString("ip");
                int udp = rs.getInt("udp");
                String palavra_passe = rs.getString("palavra_passe");
                
                for (int i = 0; i < clientes.size(); i++) {
                    if (username.equals(clientes.get(i).USERNAME))
                        continue ciclo_externo;
                }
                DatagramPacket pacote = new DatagramPacket(mensagem.getBytes(), mensagem.getBytes().length, InetAddress.getByName(ip), udp);
                socket_notificacoes_udp.send(pacote);
                
                try { 
                    socket_notificacoes_udp.receive(new DatagramPacket(new byte[1024], 1024));
                } 
                catch (SocketTimeoutException e) {
                    incrementaFalhasDoCliente(username);
                    if (obtemNumeroFalhasDoCliente(username) >= N_FALHAS)
                        this.desautenticarCliente(username, palavra_passe);
                }
            }
            
            rs.close();
            return true;
        } catch (ClassNotFoundException| SQLException | IOException e) {
            throw new ClienteServidorException(e.getMessage());
        } 
    }
    @Override
    public InetSocketAddress obtemIpPortoTransferenciaFicheiros(String nome_ficheiro) throws ClienteServidorException {
        
        try {
            ResultSet rs = stmt.executeQuery("SELECT ip, tcp_clientes, Ficheiros.nome FROM Ficheiros "
                    + "INNER JOIN Utilizadores ON Ficheiros.utilizador_id = Utilizadores.utilizador_id AND autenticado = TRUE " 
                    + "AND Ficheiros.nome = '" + nome_ficheiro + "';");
            if (!rs.next())
                throw new ClienteServidorException("O ficheiro '" + nome_ficheiro + "' NAO existe");
            
            int porto = rs.getInt("tcp_clientes");
            String ip = rs.getString("ip");
            
            InetAddress addr = InetAddress.getByName(ip);
            
            if (porto < 0)
                throw new ClienteServidorException("Porto inválido");

            rs.close();
            return new InetSocketAddress(addr, porto);
            
        } catch (IOException | SQLException e) {
            throw new ClienteServidorException(e.toString());
        }
        
    }
    @Override
    public String obtemHistorialTransferencias() throws ClienteServidorException {
        
        
        String sql = "SELECT transferencia_id, username_origem, username_destino, ficheiro, tipo, ocorrencia, utilizador_id "
                + "FROM Transferencias";

        String str = "";
        
        try {
            
            ResultSet rs = stmt.executeQuery(sql);
  
            while (rs.next()) {
                
                int transferencia_id = rs.getInt("transferencia_id");
                String origem = rs.getString("username_origem");
                String destino = rs.getString("username_destino");
                String ficheiro = rs.getString("ficheiro");
                String tipo = rs.getString("tipo");
                java.sql.Timestamp ocorrencia = rs.getTimestamp("ocorrencia");
                int utilizador_id = rs.getInt("utilizador_id");
                
                str += transferencia_id;
                str += "\t" + origem;
                str += "\t" + destino;
                str += "\t" + ficheiro;
                str += "\t" + tipo;
                str += "\t" + ocorrencia;
                str += "\t" + utilizador_id;
                str += "\n";
            }
        }
        catch (SQLException e) {
            System.err.println(e);
        }
        
        return str;
    }
    @Override
    public boolean adicionaTransferenciaNoHistorial(Transferencia t) throws ClienteServidorException {
        
        String str;
        ResultSet rs;
        
        try {
            str = "SELECT utilizador_id, username FROM Utilizadores WHERE username = '" + t.origem + "'";
            rs = DriverManager.getConnection("jdbc:mysql://" + ip_bd + "/trabalho_pratico", username_bd, password_bd).createStatement().executeQuery(str);
            
            rs.next();
            int id = rs.getInt("utilizador_id");

            str = "INSERT into Transferencias(username_origem, username_destino, ficheiro, tipo, ocorrencia, utilizador_id) "
                    + "values('" + t.origem + "', '"
                    + t.destino + "', '"
                    + t.ficheiro + "', '"
                    + t.tipo + "', '"
                    + t.ocorrencia + "', "
                    + id + ");";   

            DriverManager.getConnection("jdbc:mysql://" + ip_bd + "/trabalho_pratico", username_bd, password_bd).createStatement().executeUpdate(str);
            return true;
        }
        catch (NullPointerException | SQLException e) {
            throw new ClienteServidorException(e.getMessage());
        }
    }
    @Override
    public boolean existeFicheiro(String nome_ficheiro) throws ClienteServidorException {
        
        
        String str;
        ResultSet rs;
        
        try {
            str = "SELECT nome FROM Ficheiros";
            rs = stmt.executeQuery(str);

            while (rs.next()) {
                
                String fich = rs.getString("nome");
                
                if (fich.equals(nome_ficheiro))
                    return true;
            }
            
            return false;
        }
        catch (SQLException e) {
            throw new ClienteServidorException(e.getMessage());
        }
        
    }
    @Override
    public boolean autenticadoUtilizador(String nome) {
        
        String str;
        ResultSet rs;
        
        try {
            str = "SELECT username FROM Utilizadores";
            rs = DriverManager.getConnection("jdbc:mysql://" + ip_bd + "/trabalho_pratico", username_bd, password_bd).createStatement().executeQuery(str);
            
            while (rs.next()) {
                String username = rs.getString("username");
                if (username.equals(nome))
                    return true;
            }
            return false;
        }
        catch (NullPointerException | SQLException e) {
            return false;
        }
        
    }

    // Thread atendedora de clientes (uma thread para cada cliente) 
    private class ClienteThread extends Thread {
        
        public final String THREAD_ID;

        // Socket METODOS
        private final Socket socket_metodos;
        private final ObjectInputStream in;
        private final ObjectOutputStream out;
        
        // Socket NOTIFICACOES
        private final Socket socket_notificacoes;
        private final ObjectOutputStream out_notificacoes;
        
        // Dados da CONTA
        private String USERNAME, PASSWORD;
        
        public ClienteThread(Socket socket_metodos, Socket socket_notificacoes) throws IOException {
            
            this.socket_metodos = socket_metodos;
            this.socket_notificacoes = socket_notificacoes;
            
            in = new ObjectInputStream(socket_metodos.getInputStream());
            out = new ObjectOutputStream(socket_metodos.getOutputStream());
            
            out_notificacoes = new ObjectOutputStream(socket_notificacoes.getOutputStream());
            
            THREAD_ID = getName();
        }
        
        @Override
        public void run() {
            atendeCliente();
        }
        private void atendeCliente() {
            
            ciclo_mensagens: while (true) {
                
                try {
                    
                    Mensagem msg = (Mensagem) in.readObject();
                    int metodo_id = msg.getMetodo();
                    
                    
                    switch (metodo_id) {
                        case 0:
                            System.out.println("[Info] Thread [" + THREAD_ID + "]: a terminar por pedido do cliente");
                            break ciclo_mensagens;
                        case Metodos.ADICIONAR_CLIENTE:
                            msg = adicionaCliente(msg);
                            break;
                        case Metodos.AUTENTICAR_CLIENTE:
                            msg = autenticaCliente(msg);
                            break;
                        case Metodos.REMOVER_CLIENTE:
                            msg = removeCliente(msg);
                            break;
                        case Metodos.DESAUTENTICAR_CLIENTE:
                            msg = desautenticaCliente(msg);
                            break;
                        case Metodos.ADICIONAR_FICHEIRO_CLIENTE:
                            msg = adicionaFicheiroCliente(msg);
                            break;
                        case Metodos.REMOVER_FICHEIRO_CLIENTE:
                            msg = removeFicheiroCliente(msg);
                            break;
                        case Metodos.OBTEM_UTILIZADORES_E_FICHEIROS:
                            msg = utilizadoresEFicheiros(msg);
                            break;
                        case Metodos.ENVIAR_MSG_A_CLIENTE:
                            msg = enviaMensagemAoCliente(msg);
                            break;
                        case Metodos.ENVIAR_MSG_AOS_CLIENTES:
                            msg = enviaMensagemAosClientes(msg);
                            break;
                        case Metodos.OBTEM_IP_PORTO_TRANSFERENCIA:
                            msg = obtemIPPortoTransferencia(msg);
                            break;
                        case Metodos.OBTEM_HISTORIAL_TRANSFERENCIAS:
                            msg = obtemHistorialTransferencias(msg);
                            break;
                        case Metodos.ADICIONA_TRANSFERENCIA_AO_HISTORIAL:
                            msg = adicionaTransferenciaNoHistorial(msg);
                            break;
                        case Metodos.EXISTE_FICHEIRO:
                            msg = existeFicheiro(msg);
                            break;
                        case Metodos.AUTENTICADO_UTILIZAODR:
                            msg = existeFicheiro(msg);
                            break;
                        default:
                            msg = comandoInvalido(msg);
                            System.out.println("Thread (" + THREAD_ID + "): escolha inválida de um dos metodos");
                    }
                    out.writeUnshared(msg);
                    out.flush();
                }
                catch (Exception e) {
                    if (e instanceof ClassNotFoundException) {
                        System.out.println("Thread [" + THREAD_ID + "]: Problema ao fazer cast da mensagem do cliente.\n");
                        continue;
                    }
                    System.err.println("-> Cliente terminou: " + e);    
                    System.out.println("[Info] O cliente [" + THREAD_ID + "] terminou.");
                    break;
                }
            
            } // Fim do ciclo WHILE
            
            Destructor(); 
        }   
        private void Destructor() {
            
            if (USERNAME != null && PASSWORD != null) {
                try {
                    Mensagem m = new Mensagem();
                    m.addParametros(USERNAME);
                    m.addParametros(PASSWORD);
                    
                    synchronized(clientes) {
                        Iterator<ClienteThread> it = clientes.iterator();
                        while (it.hasNext()) {
                            ClienteThread c = it.next();
                            if (c.getId() == this.getId()) {
                                it.remove();
                                desautenticaCliente(m);
                                break;
                            }
                        }
                    }
                } 
                catch (Exception e) {
                    System.err.println("[Erro] Problema no destrutor 1 " + e);
                }
            }
            else {
                try {
                    synchronized(clientes) {
                        Iterator<ClienteThread> it = clientes.iterator();
                        while (it.hasNext()) {
                            ClienteThread c = it.next();
                            if (c.getId() == this.getId()) {
                                it.remove();
                                break;
                            }
                        }
                    }
                } 
                catch (Exception e) {
                    System.err.println("[Erro] Problema no destrutor 1 " + e);
                }
            }
            
            try {
                out_notificacoes.close();
                out.close();
                in.close();
                socket_notificacoes.close();
                socket_metodos.close();
            } 
            catch (IOException e) {
                System.err.println("[Erro] Problema no destrutor 2 " + e);
            }
            
            synchronized(clientes) {
                System.out.println("[Info] Clientes existentes = " + clientes.size() + ": " + clientes + "\n");
            }
        }
        
        private Mensagem adicionaCliente(Mensagem msg) {
            
            Mensagem nova_msg = new Mensagem();
            nova_msg.copiarAPartirDaMensagem(msg);
            boolean resultado = false;
            
            if (USERNAME != null) {
                nova_msg.setResultado(resultado, true, "Autenticado com outra conta. Para registar outra conta, desautenticar desta primeiro");
                return nova_msg;
            }
            
            try {

                String nome = msg.getParametroNaPosicao(0);
                String username = msg.getParametroNaPosicao(1);
                String password = msg.getParametroNaPosicao(2);   
                
                resultado = Servidor.this.adicionarCliente(nome, username, password);
                Servidor.this.enviarMensagemATodosOsClientes("SERVIDOR", "", "Novo registo", "Novo registo = " + username); // NOTIFICACAO
                nova_msg.setResultado(resultado, false, "");
                
                /* Notificar RMI */
                synchronized (sensor) {
                    try { sensor.notificaListeners("Registado novo cliente: " + username); } catch (RemoteException e) {}
                }
                
            } catch (ClienteServidorException | ArrayIndexOutOfBoundsException e) {
                System.err.println("Thread " + getId() + ": " + e);
                nova_msg.setResultado(resultado, true, e.getMessage());
            }
            
            return nova_msg;
        }
        private Mensagem autenticaCliente(Mensagem msg) {
            
            Mensagem nova_msg = new Mensagem();
            nova_msg.copiarAPartirDaMensagem(msg);
            boolean resultado = false;
            
            if (USERNAME != null) {
                nova_msg.setResultado(resultado, true, "Autenticado com outra conta. Para autenticar outra conta, desautenticar desta primeiro");
                return nova_msg;
            }
            
            try {

                String username = msg.getParametroNaPosicao(0);
                String password = msg.getParametroNaPosicao(1);
                String ip = msg.getParametroNaPosicao(2);   
                int udp = Integer.valueOf(msg.getParametroNaPosicao(3)); 
                int tcp_transf_clientes = Integer.valueOf(msg.getParametroNaPosicao(4)); 
                int tcp_servidor = Integer.valueOf(msg.getParametroNaPosicao(5)); 
                int tcp_notificacoes_servidor = Integer.valueOf(msg.getParametroNaPosicao(6)); 
                
                resultado = Servidor.this.autenticarCliente(username, password, ip, udp, tcp_transf_clientes, tcp_servidor, tcp_notificacoes_servidor);
                nova_msg.setResultado(resultado, false, "");
                USERNAME = username;
                PASSWORD = password;
                
                Servidor.this.enviarMensagemATodosOsClientes("SERVIDOR", "", "Cliente autenticado", "Autenticado '" + username + "'"); // NOTIFICACAO
                
                /* Notificar RMI */
                synchronized (sensor) {
                    try { sensor.notificaListeners("Cliente '" + username + "' autenticado"); } catch (RemoteException e) {}
                }
                
            } catch (ClienteServidorException | ArrayIndexOutOfBoundsException | NumberFormatException e) {
                System.err.println("Thread " + getId() + ": " + e);
                nova_msg.setResultado(resultado, true, e.getMessage());
            }
            
            return nova_msg;
        }
        private Mensagem removeCliente(Mensagem msg) {
            
            Mensagem nova_msg = new Mensagem();
            nova_msg.copiarAPartirDaMensagem(msg);
            boolean resultado = false;
            
            try {

                String username = msg.getParametroNaPosicao(0);
                String password = msg.getParametroNaPosicao(1);
                
                if (USERNAME != null) {
                    nova_msg.setResultado(resultado, true, "Autenticado com uma conta. Para remover uma conta, desautenticar desta primeiro");
                    return nova_msg;
                }
                
                resultado = Servidor.this.removerCliente(username, password);
                Servidor.this.enviarMensagemATodosOsClientes("SERVIDOR", "", "Registo removido", "Removido = " + username); // NOTIFICACAO
                nova_msg.setResultado(resultado, false, "");
                
                /* Notificar RMI */
                synchronized (sensor) {
                    try { sensor.notificaListeners("Cliente '" + username + "' apagado."); } catch (RemoteException e) {}
                }
                
            } catch (ClienteServidorException | ArrayIndexOutOfBoundsException | NumberFormatException e) {
                System.err.println("Thread " + getId() + ": " + e);
                nova_msg.setResultado(resultado, true, e.getMessage());
            }
            
            return nova_msg;
        }
        private Mensagem desautenticaCliente(Mensagem msg) {
            
            Mensagem nova_msg = new Mensagem();
            nova_msg.copiarAPartirDaMensagem(msg);
            boolean resultado = false;
            
            try {

                String username = msg.getParametroNaPosicao(0);
                String password = msg.getParametroNaPosicao(1);
                
                // Se eu estiver a desautenticar outra pessoa, isto não deve ser possível
                if (!username.equalsIgnoreCase(USERNAME)) {
                    nova_msg.setResultado(resultado, true, "Não é possível desautenticar outra conta");
                    return nova_msg;
                }
                
                Servidor.this.enviarMensagemATodosOsClientes("SERVIDOR", "", "Cliente desautenticado", "Desautenticado = " + username); // NOTIFICACAO
                
                resultado = Servidor.this.desautenticarCliente(username, password);
                nova_msg.setResultado(resultado, false, "");
                
                // Desautenticado - apagar o username associado a esta ligacao
                USERNAME = null;
                PASSWORD = null;
                
                /* Notificar RMI */
                synchronized (sensor) {
                    try { sensor.notificaListeners("Cliente '" + username + "' desautenticado."); } catch (RemoteException e) {}
                }
                
            } catch (ClienteServidorException | ArrayIndexOutOfBoundsException | NumberFormatException e) {
                System.err.println("Thread " + getId() + ": " + e);
                nova_msg.setResultado(resultado, true, e.getMessage());
            }
            
            return nova_msg;
        }
        private Mensagem adicionaFicheiroCliente(Mensagem msg) {
            
            Mensagem nova_msg = new Mensagem();
            nova_msg.copiarAPartirDaMensagem(msg);
            boolean resultado = false;
            
            try {

                String username = msg.getParametroNaPosicao(0);
                String password = msg.getParametroNaPosicao(1);
                String ficheiro = msg.getParametroNaPosicao(2);
                
                if (!username.equalsIgnoreCase(USERNAME)) {
                    nova_msg.setResultado(resultado, true, "Autenticado com uma conta. Para adiciona um novo ficheiro com uma outra conta, desautenticar desta primeiro");
                    return nova_msg;
                }
                
                resultado = Servidor.this.adicionarFicheiroCliente(username, password, ficheiro);
                Servidor.this.enviarMensagemATodosOsClientes("SERVIDOR", "", "Novo ficheiro", "O cliente '" + username + "' adicionou o ficheiro '" + ficheiro + "'"); // NOTIFICACAO
                nova_msg.setResultado(resultado, false, "");
                
                /* Notificar RMI */
                synchronized (sensor) {
                    try { sensor.notificaListeners("O Cliente '" + username + "' adicionou o ficheiro: " + ficheiro); } catch (RemoteException e) {}
                }
                
            } catch (ClienteServidorException | ArrayIndexOutOfBoundsException | NumberFormatException e) {
                System.err.println("Thread " + getId() + ": " + e);
                nova_msg.setResultado(resultado, true, e.getMessage());
            }
            
            
            
            return nova_msg;
        }
        private Mensagem removeFicheiroCliente(Mensagem msg) {
            
            Mensagem nova_msg = new Mensagem();
            nova_msg.copiarAPartirDaMensagem(msg);
            boolean resultado = false;
            
            try {

                String username = msg.getParametroNaPosicao(0);
                String password = msg.getParametroNaPosicao(1);
                String ficheiro = msg.getParametroNaPosicao(2);
                
                if (!username.equalsIgnoreCase(USERNAME)) {
                    nova_msg.setResultado(resultado, true, "Autenticado com uma conta. Para remove um ficheiro com uma outra conta, desautenticar desta primeiro");
                    return nova_msg;
                }
                
                resultado = Servidor.this.removerFicheiroCliente(username, password, ficheiro);
                Servidor.this.enviarMensagemATodosOsClientes("SERVIDOR", "", "Ficheiro removido", "O cliente '" + username + "' removeu o ficheiro '" + ficheiro + "'"); // NOTIFICACAO
                nova_msg.setResultado(resultado, false, "");
                
                            
                /* Notificar RMI */
                synchronized (sensor) {
                    try { sensor.notificaListeners("O Cliente '" + username + "' apagou o ficheiro: " + ficheiro); } catch (RemoteException e) {}
                }
                
            } catch (ClienteServidorException | ArrayIndexOutOfBoundsException | NumberFormatException e) {
                System.err.println("Thread " + getId() + ": " + e);
                nova_msg.setResultado(resultado, true, e.getMessage());
            }
            
            return nova_msg;
        }
        private Mensagem utilizadoresEFicheiros(Mensagem msg) {
            
            Mensagem nova_msg = new Mensagem();
            nova_msg.copiarAPartirDaMensagem(msg);
            
            try {
                
                Utilizadores resultado = Servidor.this.obtemUtilizadoresEFicheiros();
                nova_msg.setResultado(resultado, false, "");
                
            } catch (ClienteServidorException | ArrayIndexOutOfBoundsException | NumberFormatException e) {
                System.err.println("Thread " + getId() + ": " + e);
                nova_msg.setResultado(null, true, e.getMessage());
            }
            
            return nova_msg;
        }
        private Mensagem enviaMensagemAoCliente(Mensagem msg) {
            
            Mensagem nova_msg = new Mensagem();
            nova_msg.copiarAPartirDaMensagem(msg);
            boolean resultado = false;
            
            try {

                String username_origem = msg.getParametroNaPosicao(0);
                String password = msg.getParametroNaPosicao(1);
                String username_destino = msg.getParametroNaPosicao(2);
                String titulo = msg.getParametroNaPosicao(3);
                String descricao = msg.getParametroNaPosicao(4);
                
                if (!username_origem.equalsIgnoreCase(USERNAME)) {
                    nova_msg.setResultado(resultado, true, "Autenticado com outra uma conta. Para enviar, desautenticar desta primeiro");
                    return nova_msg;
                }
                
                resultado = Servidor.this.enviarMensagemAoCliente(username_origem, password, username_destino, titulo, descricao);
                nova_msg.setResultado(resultado, false, "");
                
            } catch (ClienteServidorException | ArrayIndexOutOfBoundsException | NumberFormatException e) {
                System.err.println("Thread " + getId() + ": " + e);
                nova_msg.setResultado(resultado, true, e.getMessage());
            }
            
            return nova_msg;
        }
        private Mensagem enviaMensagemAosClientes(Mensagem msg) {
            
            Mensagem nova_msg = new Mensagem();
            nova_msg.copiarAPartirDaMensagem(msg);
            boolean resultado = false;
            
            try {

                String username_origem = msg.getParametroNaPosicao(0);
                String password = msg.getParametroNaPosicao(1);
                String titulo = msg.getParametroNaPosicao(2);
                String descricao = msg.getParametroNaPosicao(3);
                
                resultado = Servidor.this.enviarMensagemATodosOsClientes(username_origem, password, titulo, descricao);
                nova_msg.setResultado(resultado, false, "");
                
            } catch (ClienteServidorException | ArrayIndexOutOfBoundsException | NumberFormatException e) {
                System.err.println("Thread " + getId() + ": " + e);
                nova_msg.setResultado(resultado, true, e.getMessage());
            }
            
            return nova_msg;
        }
        private Mensagem obtemIPPortoTransferencia(Mensagem msg) {
            
            Mensagem nova_msg = new Mensagem();
            nova_msg.copiarAPartirDaMensagem(msg);
            SocketAddress resultado = null;
            
            try {

                String ficheiro = msg.getParametroNaPosicao(0);
                
                resultado = Servidor.this.obtemIpPortoTransferenciaFicheiros(ficheiro);
                nova_msg.setResultado(resultado, false, "");
                
            } catch (ClienteServidorException | ArrayIndexOutOfBoundsException | NumberFormatException e) {
                System.err.println("Thread " + getId() + ": " + e);
                nova_msg.setResultado(resultado, true, e.getMessage());
            }
            
            return nova_msg;
        }
        private Mensagem comandoInvalido(Mensagem msg) {
            
            Mensagem nova_msg = new Mensagem();
            nova_msg.copiarAPartirDaMensagem(msg);
            
            nova_msg.setResultado(null, true, "Comando invalido");
            return nova_msg;
            
        }
        private Mensagem obtemHistorialTransferencias(Mensagem msg) {
            
            Mensagem nova_msg = new Mensagem();
            nova_msg.copiarAPartirDaMensagem(msg);
            
            try {
                String str = "id_transf\torigem\tdestino\tficheiro\ttipo\tdata\tid_util\n" + Servidor.this.obtemHistorialTransferencias();
                            
                nova_msg.setResultado(str, false, "");
                return nova_msg;
            }
            catch (ClienteServidorException e) {
                System.err.println("[ERRO] Problema na base de dados " + e);
                nova_msg.setResultado(null, true, "[ERRO] Problema na base de dados " + e.getMessage());
                return nova_msg;
            }
            
        }
        private Mensagem adicionaTransferenciaNoHistorial(Mensagem msg) {
            
            Mensagem nova_msg = new Mensagem();
            nova_msg.copiarAPartirDaMensagem(msg);
            
            try {
                
                String origem = msg.getParametroNaPosicao(0);
                String destino = msg.getParametroNaPosicao(1);
                String ficheiro = msg.getParametroNaPosicao(2);
                String tipo = msg.getParametroNaPosicao(3);
                java.sql.Timestamp ocorrencia = java.sql.Timestamp.valueOf(msg.getParametroNaPosicao(4));
                
                Transferencia t = new Transferencia(origem, destino, ficheiro, tipo, ocorrencia);
                boolean b = Servidor.this.adicionaTransferenciaNoHistorial(t);

                nova_msg.setResultado(b, false, "");
                return nova_msg;
            }
            catch (ClienteServidorException e) {
                System.err.println("[ERRO] Problema na base de dados " + e);
                nova_msg.setResultado(e, true, "[ERRO] Problema na base de dados " + e.getMessage());
                return nova_msg;
            }
            
        }
        private Mensagem existeFicheiro(Mensagem msg) {
            
            Mensagem nova_msg = new Mensagem();
            nova_msg.copiarAPartirDaMensagem(msg);
            
            try {
                
                boolean resultado = Servidor.this.existeFicheiro(msg.getParametroNaPosicao(0));
                nova_msg.setResultado(resultado, false, "");
                
            } catch (ClienteServidorException | ArrayIndexOutOfBoundsException | NumberFormatException e) {
                System.err.println("Thread " + getId() + ": " + e);
                nova_msg.setResultado(null, true, e.getMessage());
            }
            
            return nova_msg;
        }
        
        public ObjectOutputStream getOutNotificacoes() {
            return out_notificacoes;
        }
     
        public boolean estaAutenticado() {
            return USERNAME != null;
        }
        
        @Override
        public String toString() {
            
            if (USERNAME == null)
                return "[" + THREAD_ID + " - CLIENTE: NULL]";
            else
                return "[" + THREAD_ID + " - CLIENTE: " + USERNAME + "]";
        }
    } 
    // Thread que envia mensagens keepalive aos utilizadores autenticados, caso o numero de falhas seja igual ou superior a N_FALHAS desautentica o utilizador em causa
    private class KeepAliveThread extends Thread {
        
        private volatile boolean terminar = false;
        
        private synchronized void keepAlive() throws InterruptedException, ClassNotFoundException, SQLException, UnknownHostException, IOException {
            
            while (!terminar) {
                
                Thread.sleep(TIMEOUT_KEEP_ALIVE);
            
                ResultSet rs = obtemNovoStatement().executeQuery("SELECT username, palavra_passe, ip, udp FROM Utilizadores WHERE autenticado = TRUE");
                
                
                ciclo_externo: while (rs.next()) {
                
                    String username = rs.getString("username");
                    String ip = rs.getString("ip");
                    int udp = rs.getInt("udp");
                    String palavra_passe = rs.getString("palavra_passe");

                    for (int i = 0; i < clientes.size(); i++) {
                        if (username.equals(clientes.get(i).USERNAME))
                            continue ciclo_externo;
                    }

                    String msg = "Keep Alive do Servidor [" + InetAddress.getByName(ip) + ":" + keep_alive_socket.getLocalPort() + "] - " + username;
                    
                    keep_alive_socket.send(new DatagramPacket(msg.getBytes(), msg.getBytes().length, InetAddress.getByName(ip), udp));

                    try {
                        keep_alive_socket.receive(new DatagramPacket(new byte[1024], 1024));
                    }
                    catch (SocketTimeoutException e) {
                        try {
                            incrementaFalhasDoCliente(username);
                        if (obtemNumeroFalhasDoCliente(username) >= N_FALHAS)
                            Servidor.this.desautenticarCliente(username, palavra_passe);
                        } catch (ClienteServidorException | NullPointerException ex) { System.err.println("Thread KeepAlive: " + ex); }
                    }
                    
                    System.out.println(msg);
                }
            }
        }
        
        public KeepAliveThread() throws SocketException {
            keep_alive_socket = new DatagramSocket();
            keep_alive_socket.setSoTimeout(TIMEOUT_KEEP_ALIVE);
        }
        
        @Override
        public void run() {
            try {
                keepAlive();
            } catch (Exception e) {
                System.out.println("[ERRO] A terminar KeepAliveThread: " + e);
            }
        }
        
        public void termina() {
            terminar = true;
            keep_alive_socket.close();
        }
    }
    
// Servico RMI 
    public class Sensor extends UnicastRemoteObject implements ISensor {
        public Sensor() throws RemoteException {}
        // Métodos RMI
        @Override
        public void adicionarListener(IListener listener) throws RemoteException {
            listeners.add(listener);        
            System.out.println("[Info] Registado novo listener");
        }
        @Override
        public void removerListener(IListener listener) throws RemoteException {
            synchronized (listeners) {
                listeners.remove(listener);        
            }
            System.out.println("[Info] Removido listener");
        }
        @Override
        public Utilizadores obtemUtilizadores() throws RemoteException {

            Utilizadores u = null;
            try {
                u = obtemUtilizadoresEFicheiros();
            }
            catch (ClienteServidorException e) {}

            return u; 
        }
        @Override
        public synchronized void notificaListeners(final String msg) {
        
            Iterator<IListener> it = listeners.iterator();
            
            while (it.hasNext()) {
                IListener l = it.next();
                try { 
                    l.actualizar(msg);
                }
                catch (RemoteException e) {
                    System.out.println ("[Info] Um listener terminou.");
                    it.remove();
                }
            } // Fim do While
        }
    }
}

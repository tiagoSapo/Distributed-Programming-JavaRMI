
package modelo.comunicacao;

import java.net.InetSocketAddress;

public interface ComunicacaoClienteServidor {
    
    public boolean adicionarCliente(String nome, String username, String password) throws ClienteServidorException;
    public boolean removerCliente(String username, String password) throws ClienteServidorException;
    
    public boolean autenticarCliente(String username, String password, String ip, int udp, int tcp_transf_clientes, int tcp_servidor, int tcp_notificacoes_servidor) throws ClienteServidorException;
    public boolean desautenticarCliente(String username, String password) throws ClienteServidorException;
    
    public boolean adicionarFicheiroCliente(String username, String password, String nome_ficheiro) throws ClienteServidorException;
    public boolean removerFicheiroCliente(String username, String password, String nome_ficheiro) throws ClienteServidorException;
    
    public boolean enviarMensagemAoCliente(String username_origem, String password, String username_destino, String titulo, String descricao) throws ClienteServidorException;
    public boolean enviarMensagemATodosOsClientes(String username_origem, String password, String titulo, String descricao) throws ClienteServidorException;
    
    public InetSocketAddress obtemIpPortoTransferenciaFicheiros(String nome_ficheiro) throws ClienteServidorException;
    public Utilizadores obtemUtilizadoresEFicheiros() throws ClienteServidorException;
    
    public String obtemHistorialTransferencias() throws ClienteServidorException;
    public boolean adicionaTransferenciaNoHistorial(Transferencia t) throws ClienteServidorException;
    public boolean existeFicheiro(String nome_ficheiro) throws ClienteServidorException;
    public boolean autenticadoUtilizador(String nome);
}


package modelo.comunicacao;

public interface Metodos {
    public static final int ADICIONAR_CLIENTE = 1;
    public static final int REMOVER_CLIENTE = 2;
    public static final int AUTENTICAR_CLIENTE = 3;
    public static final int DESAUTENTICAR_CLIENTE = 4;
    public static final int ADICIONAR_FICHEIRO_CLIENTE = 5;
    public static final int REMOVER_FICHEIRO_CLIENTE = 6;
    public static final int OBTEM_UTILIZADORES_E_FICHEIROS = 7;
    public static final int ENVIAR_MSG_A_CLIENTE = 8;
    public static final int ENVIAR_MSG_AOS_CLIENTES = 9;
    public static final int OBTEM_IP_PORTO_TRANSFERENCIA = 10;
    public static final int OBTEM_HISTORIAL_TRANSFERENCIAS = 11;
    public static final int ADICIONA_TRANSFERENCIA_AO_HISTORIAL = 12;
    public static final int EXISTE_FICHEIRO = 13;
    public static final int AUTENTICADO_UTILIZAODR = 14;
}

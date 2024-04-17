
package modelo.comunicacao;

import java.io.Serializable;
import java.sql.Date;
import java.util.ArrayList;


public class Mensagem implements Serializable {
    
    long serialVersionUID = 1L;
    
    private int metodo; 
    private ArrayList<String> parametros;
    private Object resultado;
    
    private boolean gerou_excepcao;
    private String mensagem_excepcao;
    
    public Mensagem() {
        metodo = -1;
        parametros = new ArrayList<>();
        resultado = null;
        
        gerou_excepcao = false;
        mensagem_excepcao = "";
    }
    
    public void setMetodo(int m) {
        metodo = m;
    }
    public int getMetodo() {
        return metodo;
    }
    
    public void addParametros(String param) {
        parametros.add(param);
    }
    public String getParametroNaPosicao(int pos){
        if (pos >= 0 && pos < parametros.size())
            return parametros.get(pos);
        else
            throw new ArrayIndexOutOfBoundsException("Posição inválida na obtenção do parametro: pos = " + pos);
    }
    
    public Object getResultado() {
        return resultado;
    }
    public void setResultado(Object result, boolean excepcao, String msg_excepcao) {
        if (!excepcao)
            resultado = result;
        else
            resultado = null;
        gerou_excepcao = excepcao;
        mensagem_excepcao = msg_excepcao;
    }
    
    public boolean getExcepcao() {
        return gerou_excepcao;
    }
    public String getMsgExcepcao() {
        return mensagem_excepcao;
    }
    
    public void copiarAPartirDaMensagem(Mensagem msg) {
        this.gerou_excepcao = msg.gerou_excepcao;
        this.mensagem_excepcao = msg.mensagem_excepcao;
        this.metodo = msg.metodo;
        this.parametros = msg.parametros;
        this.resultado = msg.resultado;
    }
    
    @Override
    public String toString() {
        return metodo + "," + resultado + ", " + gerou_excepcao;
    }
    
}

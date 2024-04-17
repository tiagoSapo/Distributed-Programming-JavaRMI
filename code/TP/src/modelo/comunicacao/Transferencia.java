
package modelo.comunicacao;

import java.io.Serializable;
import java.sql.Date;

public class Transferencia implements Serializable {
    
    long serialVersionUID = 1L;
    
    public String origem, destino, ficheiro, tipo;
    public java.sql.Timestamp ocorrencia;
    
    public Transferencia(String origem, String destino, String ficheiro, String tipo, java.sql.Timestamp ocorrencia) {

        this.origem = origem;
        this.destino = destino;
        this.ficheiro = ficheiro;
        this.tipo = tipo;
        this.ocorrencia = ocorrencia;
        
    }
     
}

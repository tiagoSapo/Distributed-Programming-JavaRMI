
package modelo.comunicacao;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;

public class Utilizadores implements Serializable {
    
    long serialVersionUID = 1L;
    
    // <Username, Respectivos ficheiros>
    HashMap<String, ArrayList<String>> utilizadores = new HashMap<>();
    
    public void adicionaUtilizador(String username) {
        utilizadores.put(username, new ArrayList<>());
    }
    public void adicionaFicheiroAoUtilizador(String username, String ficheiro) {
        utilizadores.get(username).add(ficheiro);
    }
    
    public ArrayList<String> obtemFicheirosDoUtilizador(String username) {
        return utilizadores.get(username);
    }
    
    public boolean existeUtilizadorComNome(String nome) {
        return utilizadores.containsKey(nome);
    }
    
    @Override
    public String toString() {
        
        String str = "";
        
        for (Entry<String, ArrayList<String>> entry : utilizadores.entrySet() ) {
            str += entry.getKey();
            str += entry.getValue() + "\n";
        }
    
        return str;
    }
}

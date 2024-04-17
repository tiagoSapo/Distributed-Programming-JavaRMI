
package main;

import java.rmi.RemoteException;
import modelo.ClienteRMI;

public class StartClienteRMI {
    
    public static void main(String[] args) {
        
        try {
            ClienteRMI c = new ClienteRMI();
            c.iniciar();
        }
        catch (RemoteException e) {
            System.out.println("[ERRO] Ocorreu um erro na ligação RMI ao servidor");
        }
    }
    
}

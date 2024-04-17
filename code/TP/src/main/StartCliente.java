
package main;

import java.net.UnknownHostException;
import modelo.Cliente;

public class StartCliente {

    public static void main(String[] args) throws UnknownHostException {
        
        Cliente c = new Cliente();
        c.iniciar();
    }
    
}


package modelo;

import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Scanner;
import modelo.comunicacao.Utilizadores;
import modelo.comunicacaormi.*;

public class ClienteRMI extends UnicastRemoteObject implements IListener {
    
    private static final String URL_RMI = "ServicoRMI";
    private static String IP = "127.0.0.1";
    
    public ClienteRMI() throws RemoteException {}
    
    public void iniciar() {

        try {
            
            Scanner sc = new Scanner(System.in);
            
            System.out.println("\n--------------- CLIENTE ---------------");
            System.out.println("IP do RMI: ");
            System.out.print("> ");
            
            IP = sc.next(); 

            ISensor sensor = (ISensor) LocateRegistry.getRegistry(IP, 1099).lookup(URL_RMI);
            sensor.adicionarListener(this); 
            
            String in = "";
            mostraComandos();
            
            while (true) {
                
                System.out.print("\n> ");
                in = sc.next();
                
                if (in.equalsIgnoreCase("SAIR"))
                    break;
                else if (in.equalsIgnoreCase("HELP"))
                    mostraComandos();
                else if (in.equalsIgnoreCase("UTILIZADORES")) {
                    Utilizadores u = sensor.obtemUtilizadores();
                    if (u == null)
                        System.out.println("[ERRO] Problema ao obter utilizadores");
                    else
                        System.out.println("Utilizadores:\n" + u);
                }
                else
                    System.out.println("Comando invalido '" + in + "'");
            }
            
            
        }
        catch (NotBoundException | RemoteException e) { 
            System.out.println("[ERRO] Problema no RMI: (" + e + ")");
        }
        finally {
            try { UnicastRemoteObject.unexportObject(this, true); } catch (NoSuchObjectException ex) {}
        }
        
    }
    
    @Override
    public synchronized void actualizar(String msg) throws RemoteException {
        System.out.println("\n[INFO] Ocorreu: (" + msg + ")");
    }

    private void mostraComandos() {
        System.out.println("\nComandos:\n"
                + "Inserir 'utilizadores' para visualizar os utilizadores autenticados\n"
                + "Inserir 'help' para mostrar comandos\n"
                + "Inserir 'sair' para terminar");
    }
}
        


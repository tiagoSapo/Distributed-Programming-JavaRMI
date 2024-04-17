
package modelo.comunicacaormi;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IListener extends Remote {
    void actualizar(String msg) throws RemoteException; 
}

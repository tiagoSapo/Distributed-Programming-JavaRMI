
package modelo.comunicacaormi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import modelo.comunicacao.Utilizadores;

public interface ISensor extends Remote {
    void adicionarListener(IListener listener) throws RemoteException; 
    void removerListener(IListener listener) throws RemoteException;
    Utilizadores obtemUtilizadores() throws RemoteException;
    
    void notificaListeners(final String msg) throws RemoteException;
}


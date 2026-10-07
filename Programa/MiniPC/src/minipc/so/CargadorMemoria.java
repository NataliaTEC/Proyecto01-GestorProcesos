package minipc.so;

import minipc.ensamblador.Instruccion;
import minipc.hardware.Memoria;

import java.util.List;

/**
 * Conecta las instrucciones ya validadas (por ProcesadorInstrucciones) con la Memoria:
 *   1) Las escribe en el espacio de Usuario, en orden, a partir de la BASE que le asigno GestorMemoria.
 *   2) Crea el BCP del proceso (PC apuntando a la primera instruccion cargada).
 *   3) Escribe ese BCP en su RANURA del Kernel (direccion que tambien asigno GestorMemoria).
 *
 * El cargador no decide DONDE va el programa: solo copia. La decision (primer ajuste y ranura libre)
 * es de GestorMemoria.
 * @author Natalia Granados Rosales
 */
public class CargadorMemoria {

    /**
     * Carga un programa ya validado en la Memoria y devuelve el BCP del proceso creado.
     *
     * @param instrucciones   lista de instrucciones ya validadas (leidas del disco).
     * @param memoria         memoria donde se va a cargar el programa.
     * @param base            primera posicion del area de usuario asignada al programa.
     * @param posicionBCP     direccion de la ranura del Kernel donde se guardara el BCP.
     * @param pid             identificador que se le asignara al proceso.
     * @param nombrePrograma  nombre del archivo en el disco (ej: "suma.asm").
     * @return el BCP del proceso, todavia sin encolar (la cola de listos lo deja en estado LISTO).
     * @throws IllegalArgumentException si la lista viene vacia o contiene instrucciones invalidas.
     * @throws SecurityException si el programa no cabe desde la base dentro del area de usuario.
     */
    public BCP cargar(List<Instruccion> instrucciones, Memoria memoria, int base, int posicionBCP,
                      int pid, String nombrePrograma) {
        if (instrucciones == null || instrucciones.isEmpty()) {
            throw new IllegalArgumentException("No hay instrucciones para cargar en memoria.");
        }

        for (Instruccion instruccion : instrucciones) {
            if (!instruccion.isValida()) {
                throw new IllegalArgumentException(
                        "No se puede cargar un programa con errores. Línea " + instruccion.getNumeroLinea() + ": " + instruccion.getMensajeError());
            }
        }

        int fin = base + instrucciones.size() - 1;
        if (!memoria.esEspacioUsuario(base) || !memoria.esEspacioUsuario(fin)) {
            throw new SecurityException("El programa (posiciones " + base + "-" + fin + ") no cabe en el área de usuario ("
                    + memoria.getInicioUsuario() + "-" + memoria.getFinUsuario() + ").");
        }

        int posicion = base;
        for (Instruccion instruccion : instrucciones) {
            posicion = memoria.escribirInstruccion(posicion, instruccion);
        }

        BCP bcp = new BCP(pid, nombrePrograma, base, posicion - 1);
        bcp.setPosicionBCP(posicionBCP);
        memoria.guardarBCP(bcp);
        return bcp;
    }
}
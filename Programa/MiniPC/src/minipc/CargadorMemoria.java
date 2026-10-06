package minipc;

import java.util.List;
/**
 * Conecta las instrucciones ya validadas (por {@link ProcesadorInstrucciones}) con la {@link Memoria}: las escribe en el espacio de Usuario, en orden
 * crea el {@link BCP} del proceso ya listo para ejecutarse (PC apuntando a la primera instruccion cargada)
 * y escribe ese BCP en el espacio de Kernel (posicion 0).
 * @author Natalia Granados Rosales
 */
public class CargadorMemoria {
    /**
     * Carga un programa ya validado en la Memoria, y devuelve el BCP del proceso creado.
     *
     * @param instrucciones lista de instrucciones ya validadas.
     * @param memoria       memoria donde se va a cargar el programa.
     * @param pid           identificador que se le asignara al proceso.
     * @return el BCP del proceso, con estado LISTO y el PC en la primera instrucción.
     * @throws IllegalArgumentException si la lista viene vacía o contiene instrucciones invalidas.
     */
    public BCP cargar(List<Instruccion> instrucciones, Memoria memoria, int pid) {
        if (instrucciones == null || instrucciones.isEmpty()) {
            throw new IllegalArgumentException("No hay instrucciones para cargar en memoria.");
        }

        for (Instruccion instruccion : instrucciones) {
            if (!instruccion.isValida()) {
                throw new IllegalArgumentException(
                        "No se puede cargar un programa con errores. Línea " + instruccion.getNumeroLinea() + ": " + instruccion.getMensajeError());
            }
        }

        int posicionInicio = memoria.getInicioUsuario();
        int posicion = posicionInicio;

        for (Instruccion instruccion : instrucciones) {
            posicion = memoria.escribirInstruccion(posicion, instruccion);
        }

        int posicionFinal = posicion - 1;

        BCP bcp = new BCP(pid, posicionInicio, posicionFinal);
        bcp.setEstado(EstadoProceso.LISTO);

        // El BCP se guarda en el espacio de Kernel, a partir de la posicion 0
        bcp.setPosicionBCP(memoria.getInicioKernel());
        memoria.guardarBCP(bcp);
        return bcp;
    }
}
package minipc.so;

import minipc.hardware.Memoria;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Cola de procesos LISTOS, en orden de llegada (FCFS), implementada como una lista enlazada
 * DENTRO DE LA MEMORIA:
 *
 *   encabezado[0] (primer listo) --> BCP en 16 --enlace--> BCP en 4 --enlace--> BCP en 40 --enlace--> -1
 *   encabezado[1] (ultimo listo) ----------------------------------------------^
 *
 * El "enlace al siguiente BCP" es la DIRECCION de memoria donde empieza el siguiente BCP (campo 11),
 * no una referencia de Java. En Java se mantiene tambien una lista de objetos BCP por comodidad,
 * pero cada vez que la cola cambia se actualizan los enlaces de los BCP afectados y los campos
 * 0 y 1 del encabezado, para que la memoria siempre muestre la lista real.
 *
 * @author Natalia Granados Rosales
 */
public class ColaListos {

    private final Memoria memoria;
    private final LinkedList<BCP> procesos = new LinkedList<>();

    public ColaListos(Memoria memoria) {
        if (memoria == null) {
            throw new IllegalArgumentException("La cola de listos necesita una memoria.");
        }
        this.memoria = memoria;
        actualizarEncabezado();
    }

    /**
     * Agrega el proceso al FINAL de la cola (FCFS): el que era el ultimo pasa a apuntar a este,
     * y este queda con enlace -1. El proceso queda en estado LISTO.
     */
    public void encolar(BCP bcp) {
        if (bcp == null) {
            throw new IllegalArgumentException("No se puede encolar un BCP nulo.");
        }
        if (procesos.contains(bcp)) {
            throw new IllegalStateException("El proceso " + bcp.getPid() + " ya está en la cola de listos.");
        }

        bcp.setEstado(EstadoProceso.LISTO);
        bcp.setEnlaceSiguiente(BCP.SIN_ENLACE);

        if (!procesos.isEmpty()) {
            BCP anteriorUltimo = procesos.getLast();
            anteriorUltimo.setEnlaceSiguiente(bcp.getPosicionBCP());
            memoria.guardarBCP(anteriorUltimo);
        }

        procesos.addLast(bcp);
        memoria.guardarBCP(bcp);
        actualizarEncabezado();
    }

    /**
     * Saca y devuelve el PRIMER proceso de la cola (el que lleva mas tiempo esperando).
     * Su enlace queda en -1 porque ya no pertenece a la lista.
     * @return el BCP, o null si la cola esta vacia.
     */
    public BCP desencolar() {
        if (procesos.isEmpty()) {
            return null;
        }
        BCP primero = procesos.removeFirst();
        primero.setEnlaceSiguiente(BCP.SIN_ENLACE);
        memoria.guardarBCP(primero);
        actualizarEncabezado();
        return primero;
    }

    /**
     * Quita un proceso de cualquier punto de la cola (por ejemplo, al suspenderlo).
     * El BCP anterior pasa a apuntar al siguiente del que se quita.
     * @return true si el proceso estaba en la cola.
     */
    public boolean quitar(BCP bcp) {
        int indice = procesos.indexOf(bcp);
        if (indice < 0) {
            return false;
        }
        if (indice > 0) {
            BCP anterior = procesos.get(indice - 1);
            anterior.setEnlaceSiguiente(bcp.getEnlaceSiguiente());
            memoria.guardarBCP(anterior);
        }
        procesos.remove(indice);
        bcp.setEnlaceSiguiente(BCP.SIN_ENLACE);
        memoria.guardarBCP(bcp);
        actualizarEncabezado();
        return true;
    }

    /** Primer proceso de la cola sin sacarlo, o null si esta vacia. */
    public BCP verPrimero() {
        return procesos.peekFirst();
    }

    public boolean estaVacia() {
        return procesos.isEmpty();
    }

    public int getCantidad() {
        return procesos.size();
    }

    public boolean contiene(BCP bcp) {
        return procesos.contains(bcp);
    }

    /** Procesos de la cola en orden (solo lectura). */
    public List<BCP> getProcesos() {
        return Collections.unmodifiableList(procesos);
    }

    /**
     * Recorre la cola LEYENDO LA MEMORIA: empieza en el campo 0 del encabezado y sigue el campo
     * "enlace" de cada BCP hasta encontrar -1. Sirve para comprobar (y mostrar) que la lista
     * enlazada realmente esta guardada en memoria.
     * @return las direcciones de los BCP en el orden de la cola.
     */
    public List<Integer> recorrerDesdeMemoria() {
        List<Integer> direcciones = new ArrayList<>();
        int direccion = memoria.leerCampoEncabezado(Memoria.ENCABEZADO_PRIMER_LISTO);
        int limite = procesos.size() + 1; // proteccion ante un ciclo por error
        while (direccion != BCP.SIN_ENLACE && direcciones.size() < limite) {
            direcciones.add(direccion);
            direccion = memoria.leerEntero(direccion + BCP.CAMPO_ENLACE);
        }
        return direcciones;
    }

    /** Reescribe los campos 0 (primer listo) y 1 (ultimo listo) del encabezado del SO. */
    private void actualizarEncabezado() {
        int primero = procesos.isEmpty() ? Memoria.SIN_DIRECCION : procesos.getFirst().getPosicionBCP();
        int ultimo = procesos.isEmpty() ? Memoria.SIN_DIRECCION : procesos.getLast().getPosicionBCP();
        memoria.escribirCampoEncabezado(Memoria.ENCABEZADO_PRIMER_LISTO, primero);
        memoria.escribirCampoEncabezado(Memoria.ENCABEZADO_ULTIMO_LISTO, ultimo);
    }
}
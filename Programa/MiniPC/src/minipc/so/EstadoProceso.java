package minipc.so;

/**
 * Estados posibles de un proceso dentro de la Mini PC (modelo de 7 estados).
 *
 *   NUEVO: el programa esta en el disco y en la lista de trabajos, pero aun no tiene memoria asignada (por ejemplo, porque ya hay 5 procesos activos).
 *   LISTO: esta en memoria principal esperando su turno de CPU (cola FCFS).
 *   EJECUTANDO: es el proceso que esta usando la CPU.
 *   BLOQUEADO: esta en memoria principal esperando un evento (ej: INT 09H, el teclado).
 *   LISTO_SUSPENDIDO: esta listo, pero fue enviado a la memoria virtual del disco.
 *   BLOQUEADO_SUSPENDIDO: esta bloqueado y ademas fue enviado a la memoria virtual del disco.
 *   TERMINADO: finalizo (INT 20H o error); su memoria y su BCP ya se liberaron.
 *
 * En memoria (dentro del BCP) se guarda con name(), por ejemplo "LISTO_SUSPENDIDO".
 * @author Natalia Granados Rosales
 */
public enum EstadoProceso {

    NUEVO("Nuevo"),
    LISTO("Listo"),
    EJECUTANDO("Ejecutando"),
    BLOQUEADO("Bloqueado"),
    LISTO_SUSPENDIDO("Listo suspendido"),
    BLOQUEADO_SUSPENDIDO("Bloqueado suspendido"),
    TERMINADO("Terminado");

    /** Nombre legible para la interfaz (ej: "Listo suspendido"). */
    private final String nombreVisible;

    EstadoProceso(String nombreVisible) {
        this.nombreVisible = nombreVisible;
    }

    public String getNombreVisible() {
        return nombreVisible;
    }

    /** Indica si el proceso esta en la memoria virtual del disco (LISTO_SUSPENDIDO o BLOQUEADO_SUSPENDIDO). */
    public boolean estaSuspendido() {
        return this == LISTO_SUSPENDIDO || this == BLOQUEADO_SUSPENDIDO;
    }

    /** Indica si el proceso ocupa memoria principal y una ranura de BCP (LISTO, EJECUTANDO o BLOQUEADO). */
    public boolean estaEnMemoriaPrincipal() {
        return this == LISTO || this == EJECUTANDO || this == BLOQUEADO;
    }

    /** Indica si el proceso esta esperando un evento (BLOQUEADO o BLOQUEADO_SUSPENDIDO). */
    public boolean estaBloqueado() {
        return this == BLOQUEADO || this == BLOQUEADO_SUSPENDIDO;
    }
}
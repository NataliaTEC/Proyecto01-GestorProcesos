package minipc.so;

import minipc.config.Configuracion;
import minipc.hardware.Memoria;

import java.util.ArrayList;
import java.util.List;

/**
 * Administra el espacio de la memoria principal en nombre del sistema operativo.
 *
 * 1) Ranuras de BCP (espacio de Kernel).
 *    Despues del encabezado del SO hay MAX_PROCESOS_ACTIVOS ranuras, de BCP.TAMANO_EN_MEMORIA
 *    posiciones cada una. La ranura i empieza en:
 *        inicioKernel + TAMANO_ENCABEZADO_SO + i * TAMANO_EN_MEMORIA
 *    Con 256 posiciones: 4-15, 16-27, 28-39, 40-51 y 52-63.
 *    Cada vez que se ocupa o libera una ranura se actualiza el campo 2 del encabezado
 *    (cantidad de procesos activos).
 *
 * 2) Area de usuario, con asignacion contigua por PRIMER AJUSTE (first fit):
 *    se recorre el area desde el inicio y se usa el primer hueco donde quepa el programa completo.
 *
 * @author Natalia Granados Rosales
 */
public class GestorMemoria {

    private final Memoria memoria;

    /** Ranuras de BCP: true = ocupada. */
    private final boolean[] ranurasOcupadas;

    /** Posiciones del area de usuario: true = ocupada por algun programa (indice 0 = inicioUsuario). */
    private final boolean[] usuarioOcupado;

    /**
     * Crea el gestor sobre una memoria vacia y deja el encabezado del SO en su estado inicial.
     * @throws IllegalArgumentException si el Kernel no alcanza para el encabezado y todas las ranuras.
     */
    public GestorMemoria(Memoria memoria) {
        if (memoria == null) {
            throw new IllegalArgumentException("El gestor de memoria necesita una memoria.");
        }
        if (Configuracion.TAMANO_ENCABEZADO_SO < Memoria.CAMPOS_ENCABEZADO) {
            throw new IllegalStateException("El encabezado del SO (" + Configuracion.TAMANO_ENCABEZADO_SO
                    + ") es menor que los " + Memoria.CAMPOS_ENCABEZADO + " campos que usa la memoria.");
        }
        int espacioNecesario = Configuracion.getEspacioRequeridoSO();
        if (espacioNecesario > memoria.getTamanoKernel()) {
            throw new IllegalArgumentException("El Kernel (" + memoria.getTamanoKernel() + " posiciones) no alcanza para el encabezado y "
                    + Configuracion.MAX_PROCESOS_ACTIVOS + " BCP (" + espacioNecesario + " posiciones).");
        }

        this.memoria = memoria;
        this.ranurasOcupadas = new boolean[Configuracion.MAX_PROCESOS_ACTIVOS];
        this.usuarioOcupado = new boolean[memoria.getTamanoUsuario()];

        memoria.inicializarEncabezado();
    }

    // ==================================================================
    // RANURAS DE BCP (Kernel)
    // ==================================================================

    /** Cantidad total de ranuras de BCP (igual al maximo de procesos activos). */
    public int getCantidadRanuras() {
        return ranurasOcupadas.length;
    }

    /** Direccion de memoria donde empieza la ranura i (0 a getCantidadRanuras() - 1). */
    public int getDireccionRanura(int indice) {
        if (indice < 0 || indice >= ranurasOcupadas.length) {
            throw new IndexOutOfBoundsException("Ranura " + indice + " inexistente (0-" + (ranurasOcupadas.length - 1) + ").");
        }
        return memoria.getInicioKernel() + Configuracion.TAMANO_ENCABEZADO_SO + indice * BCP.TAMANO_EN_MEMORIA;
    }

    /**
     * Indice de la ranura que contiene la posicion de memoria indicada, o -1 si la posicion
     * no pertenece a ninguna ranura (encabezado, area de usuario o fuera del Kernel).
     */
    public int ranuraDePosicion(int posicion) {
        int inicioRanuras = getDireccionRanura(0);
        if (posicion < inicioRanuras) {
            return -1;
        }
        int indice = (posicion - inicioRanuras) / BCP.TAMANO_EN_MEMORIA;
        return indice < ranurasOcupadas.length ? indice : -1;
    }

    public boolean ranuraOcupada(int indice) {
        return ranurasOcupadas[indice];
    }

    /**
     * Ocupa la primera ranura libre y devuelve su direccion, para usarla con bcp.setPosicionBCP(...).
     * Actualiza la cantidad de procesos activos en el encabezado.
     * @return direccion de la ranura, o -1 si las 5 ranuras estan ocupadas.
     */
    public int asignarRanuraBCP() {
        for (int i = 0; i < ranurasOcupadas.length; i++) {
            if (!ranurasOcupadas[i]) {
                ranurasOcupadas[i] = true;
                actualizarCantidadProcesos();
                return getDireccionRanura(i);
            }
        }
        return Memoria.SIN_DIRECCION;
    }

    /**
     * Libera la ranura que empieza en la direccion indicada: borra sus 12 posiciones del Kernel
     * y actualiza la cantidad de procesos activos en el encabezado.
     */
    public void liberarRanuraBCP(int direccion) {
        int indice = ranuraDePosicion(direccion);
        if (indice < 0 || getDireccionRanura(indice) != direccion) {
            throw new IllegalArgumentException("La dirección " + direccion + " no es el inicio de una ranura de BCP.");
        }
        ranurasOcupadas[indice] = false;
        memoria.limpiarRango(direccion, direccion + BCP.TAMANO_EN_MEMORIA - 1);
        actualizarCantidadProcesos();
    }

    /** Hay al menos una ranura libre para un nuevo proceso activo. */
    public boolean hayRanuraLibre() {
        return getCantidadProcesosActivos() < ranurasOcupadas.length;
    }

    /** Cantidad de ranuras ocupadas, que es la cantidad de procesos activos. */
    public int getCantidadProcesosActivos() {
        int ocupadas = 0;
        for (boolean ocupada : ranurasOcupadas) {
            if (ocupada) {
                ocupadas++;
            }
        }
        return ocupadas;
    }

    private void actualizarCantidadProcesos() {
        memoria.escribirCampoEncabezado(Memoria.ENCABEZADO_CANTIDAD_PROCESOS, getCantidadProcesosActivos());
    }

    // ==================================================================
    // AREA DE USUARIO (primer ajuste)
    // ==================================================================

    /**
     * Reserva "tamano" posiciones contiguas en el area de usuario usando primer ajuste.
     * @return la base (primera posicion) del espacio asignado, o -1 si no hay un hueco suficiente.
     */
    public int asignar(int tamano) {
        if (tamano <= 0) {
            throw new IllegalArgumentException("El tamaño a asignar debe ser mayor que 0.");
        }
        int base = buscarPrimerHueco(tamano);
        if (base == Memoria.SIN_DIRECCION) {
            return Memoria.SIN_DIRECCION;
        }
        marcar(base, tamano, true);
        return base;
    }

    /**
     * Devuelve al area libre las "tamano" posiciones que empiezan en "base" y limpia su contenido.
     * @throws IllegalArgumentException si el rango no esta dentro del area de usuario.
     */
    public void liberar(int base, int tamano) {
        int fin = base + tamano - 1;
        if (tamano <= 0 || !memoria.esEspacioUsuario(base) || !memoria.esEspacioUsuario(fin)) {
            throw new IllegalArgumentException("El rango " + base + "-" + fin + " no está dentro del área de usuario ("
                    + memoria.getInicioUsuario() + "-" + memoria.getFinUsuario() + ").");
        }
        marcar(base, tamano, false);
        memoria.limpiarRango(base, fin);
    }

    /** Indica si existe un hueco contiguo donde quepan "tamano" posiciones. */
    public boolean cabe(int tamano) {
        return tamano > 0 && buscarPrimerHueco(tamano) != Memoria.SIN_DIRECCION;
    }

    /** Indica si la posicion de usuario esta asignada a algun programa. */
    public boolean estaOcupada(int posicion) {
        return memoria.esEspacioUsuario(posicion) && usuarioOcupado[posicion - memoria.getInicioUsuario()];
    }

    /**
     * Huecos libres del area de usuario como pares {inicio, tamano}, en orden.
     * Ej: con programas en 64-75 y 90-99 -> [{76, 14}, {100, 156}].
     */
    public List<int[]> huecosLibres() {
        List<int[]> huecos = new ArrayList<>();
        int inicioHueco = -1;
        for (int i = 0; i <= usuarioOcupado.length; i++) {
            boolean libre = i < usuarioOcupado.length && !usuarioOcupado[i];
            if (libre && inicioHueco < 0) {
                inicioHueco = i;
            } else if (!libre && inicioHueco >= 0) {
                huecos.add(new int[]{memoria.getInicioUsuario() + inicioHueco, i - inicioHueco});
                inicioHueco = -1;
            }
        }
        return huecos;
    }

    /** Total de posiciones libres del area de usuario (sumando todos los huecos). */
    public int getEspacioLibre() {
        int libres = 0;
        for (int[] hueco : huecosLibres()) {
            libres += hueco[1];
        }
        return libres;
    }

    /** Tamano del hueco libre mas grande (el programa mas grande que se puede cargar ahora). */
    public int getMayorHueco() {
        int mayor = 0;
        for (int[] hueco : huecosLibres()) {
            mayor = Math.max(mayor, hueco[1]);
        }
        return mayor;
    }

    /** Primer ajuste: la base del primer hueco con al menos "tamano" posiciones, o -1. */
    private int buscarPrimerHueco(int tamano) {
        for (int[] hueco : huecosLibres()) {
            if (hueco[1] >= tamano) {
                return hueco[0];
            }
        }
        return Memoria.SIN_DIRECCION;
    }

    private void marcar(int base, int tamano, boolean ocupado) {
        int desde = base - memoria.getInicioUsuario();
        for (int i = desde; i < desde + tamano; i++) {
            usuarioOcupado[i] = ocupado;
        }
    }

    public Memoria getMemoria() {
        return memoria;
    }
}

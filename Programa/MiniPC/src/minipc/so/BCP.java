package minipc.so;

import minipc.ensamblador.Registro;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Bloque de Control de Proceso (BCP / PCB).
 *
 * Guarda todo el estado necesario para pausar y retomar un proceso en la Mini PC:
 *   - Identificacion: PID, nombre del programa, estado y prioridad.
 *   - COPIA de los registros de la CPU (contexto): PC, IR, AC, AX, BX, CX, DX, AH, AL
 *     y la bandera de igualdad (flagIgual) que deja CMP.
 *     Los registros "reales" estan en la clase CPU; aqui solo se guarda su valor
 *     cuando el proceso sale de la CPU, para poder restaurarlo despues.
 *   - Pila del proceso: 5 posiciones y su tope.
 *   - Informacion contable: CPU asignada, hora de inicio y tiempo empleado.
 *   - Estado de E/S: archivos abiertos.
 *   - Enlace al siguiente BCP: DIRECCION de memoria (Kernel) del siguiente BCP de la lista, o -1.
 *   - Memoria asignada: base y alcance (proteccion).
 *
 * El BCP se almacena en el espacio de Kernel de la memoria, a partir de posicionBCP, y ocupa
 * siempre TAMANO_EN_MEMORIA = 12 posiciones. Como la memoria guarda texto, los datos relacionados
 * se agrupan en una misma posicion, separados por " / "
 *
 * El enlace va solo en su posicion a proposito: es el campo que forma la lista enlazada
 * de BCP dentro de la memoria.
 * @author Natalia Granados Rosales
 */
public class BCP {

    // ------------------------------------------------------------------
    // Distribucion del BCP dentro de la memoria (desplazamiento de cada campo)
    // ------------------------------------------------------------------
    public static final int CAMPO_PID_PROGRAMA = 0;
    public static final int CAMPO_ESTADO_PRIORIDAD = 1;
    public static final int CAMPO_PC = 2;
    public static final int CAMPO_IR = 3;
    public static final int CAMPO_AC = 4;
    public static final int CAMPO_REGISTROS_GENERALES = 5;
    public static final int CAMPO_AH_AL_BANDERA = 6;
    public static final int CAMPO_PILA = 7;
    public static final int CAMPO_BASE_ALCANCE = 8;
    public static final int CAMPO_CONTABLE = 9;
    public static final int CAMPO_ARCHIVOS = 10;
    public static final int CAMPO_ENLACE = 11;

    /**
     * Cantidad de posiciones de memoria que ocupa un BCP en el Kernel.
     * Con la division 25/75, una memoria de 256 deja 64 posiciones de Kernel:
     * 4 de encabezado del SO + 5 BCP x 12 = 64.
     */
    public static final int TAMANO_EN_MEMORIA = 12;

    /** Nombre de cada campo, en el mismo orden en que se guardan en memoria. */
    public static final String[] NOMBRES_CAMPOS = {
        "PID / Programa",
        "Estado / Prioridad",
        "PC",
        "IR",
        "AC",
        "AX / BX / CX / DX",
        "AH / AL / CMP",
        "Pila (tope)",
        "Base / Alcance",
        "CPU / Inicio / Empleado",
        "Archivos abiertos",
        "Enlace siguiente BCP"
    };

    /** Separador entre los datos agrupados en una misma posicion. */
    public static final String SEPARADOR = " / ";

    /** Cantidad de posiciones de la pila de cada proceso. */
    public static final int TAMANO_PILA = 5;

    /** Valor del enlace cuando no hay un BCP siguiente (fin de la lista). */
    public static final int SIN_ENLACE = -1;

    /** Texto que se muestra en un dato que todavia no tiene valor. */
    private static final String SIN_VALOR = "-";

    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ------------------------------------------------------------------
    // Identificacion
    // ------------------------------------------------------------------
    private final int pid;
    private final String nombrePrograma;
    private EstadoProceso estado;
    private int prioridad;

    /** Direccion de memoria (Kernel) donde empieza este BCP. */
    private int posicionBCP;

    // ------------------------------------------------------------------
    // Contexto de la CPU
    // ------------------------------------------------------------------

    // Contador de programa: posicion de memoria de la proxima instruccion a leer.
    private int pc;

    // Registro de instruccion: la instruccion en ensamblador que se leyo de memoria (ej: "MOV AX, 5").
    private String ir;

    // Acumulador: almacenamiento temporal para operaciones (valor real, con signo).
    private int ac;

    // Registros de proposito general AX, BX, CX, DX, AH y AL (valor real, con signo).
    private final Map<Registro, Integer> registros;

    /** Bandera de igualdad de la ultima instruccion CMP (contexto que usan JE y JNE). */
    private boolean flagIgual;

    // ------------------------------------------------------------------
    // Pila del proceso
    // ------------------------------------------------------------------

    private final int[] pila;

    /** Cantidad de elementos en la pila (0 = vacia, TAMANO_PILA = llena). El proximo PUSH va en pila[tope]. */
    private int tope;

    // ------------------------------------------------------------------
    // Informacion contable
    // ------------------------------------------------------------------

    /** CPU que tiene asignada el proceso (ej: "CPU1"), o "-" si aun no se ha ejecutado. */
    private String cpuAsignada;

    /** Hora en que el proceso empezo a ejecutarse por primera vez, o null si aun no empieza. */
    private LocalTime horaInicio;

    /** Segundos de CPU que ha usado el proceso. */
    private int tiempoEmpleado;

    /** Contador informativo para la interfaz (no se guarda en memoria). */
    private int instruccionesEjecutadas;

    // ------------------------------------------------------------------
    // E/S y lista enlazada
    // ------------------------------------------------------------------

    private final List<String> archivosAbiertos;

    /** Direccion de memoria del siguiente BCP de la lista, o SIN_ENLACE (-1). */
    private int enlaceSiguiente;

    // ------------------------------------------------------------------
    // Memoria asignada
    // ------------------------------------------------------------------

    private final int limiteInferior;
    private final int limiteSuperior;

    // ------------------------------------------------------------------
    // Constructores
    // ------------------------------------------------------------------

    /**
     * Crea el BCP de un proceso.
     * @param pid             identificador del proceso.
     * @param nombrePrograma  nombre del archivo .asm (ej: "suma.asm").
     * @param limiteInferior  base: primera posicion de memoria del programa.
     * @param limiteSuperior  ultima posicion de memoria del programa.
     */
    public BCP(int pid, String nombrePrograma, int limiteInferior, int limiteSuperior) {
        this.pid = pid;
        this.nombrePrograma = (nombrePrograma == null || nombrePrograma.trim().isEmpty())
                ? SIN_VALOR : nombrePrograma.trim();
        this.limiteInferior = limiteInferior;
        this.limiteSuperior = limiteSuperior;

        this.estado = EstadoProceso.NUEVO;
        this.prioridad = 1;
        this.posicionBCP = 0;

        this.pc = limiteInferior;
        this.ir = ""; // aun no se ha leido ninguna instruccion
        this.ac = 0;
        this.flagIgual = false;

        this.registros = new EnumMap<>(Registro.class);
        for (Registro registro : Registro.values()) {
            registros.put(registro, 0);
        }

        this.pila = new int[TAMANO_PILA];
        this.tope = 0;

        this.cpuAsignada = SIN_VALOR;
        this.horaInicio = null;
        this.tiempoEmpleado = 0;
        this.instruccionesEjecutadas = 0;

        this.archivosAbiertos = new ArrayList<>();
        this.enlaceSiguiente = SIN_ENLACE;
    }

    /**
     * Constructor anterior (sin nombre de programa), se mantiene para no romper el codigo existente.
     * Cuando CargadorMemoria reciba el nombre del archivo, conviene usar el constructor completo.
     */
    public BCP(int pid, int limiteInferior, int limiteSuperior) {
        this(pid, SIN_VALOR, limiteInferior, limiteSuperior);
    }

    // ------------------------------------------------------------------
    // Operaciones de uso
    // ------------------------------------------------------------------

    /** Avanza el PC una posicion (cada instruccion ocupa 1 posicion de memoria). */
    public void avanzarPC() {
        this.pc += 1;
    }

    /** Registra que se ejecuto una instruccion mas. */
    public void registrarInstruccionEjecutada() {
        this.instruccionesEjecutadas++;
    }

    /** Verifica si el PC sigue dentro del espacio de memoria asignado a este proceso. */
    public boolean tieneInstruccionesPendientes() {
        return pc <= limiteSuperior && estado != EstadoProceso.TERMINADO;
    }

    public int obtenerValorRegistro(Registro registro) {
        return registros.get(registro);
    }

    public void asignarValorRegistro(Registro registro, int valor) {
        registros.put(registro, valor);
    }

    // ------------------------------------------------------------------
    // Pila
    // ------------------------------------------------------------------

    /**
     * Agrega un valor en el tope de la pila (PUSH / PARAM).
     * @throws IllegalStateException si la pila ya tiene TAMANO_PILA elementos (desbordamiento).
     */
    public void apilar(int valor) {
        if (pilaLlena()) {
            throw new IllegalStateException("Desbordamiento de pila: el proceso " + pid
                    + " ya tiene " + TAMANO_PILA + " valores en la pila.");
        }
        pila[tope] = valor;
        tope++;
    }

    /**
     * Saca y devuelve el valor del tope de la pila (POP).
     * @throws IllegalStateException si la pila esta vacia.
     */
    public int desapilar() {
        if (pilaVacia()) {
            throw new IllegalStateException("Pila vacía: el proceso " + pid + " intentó sacar un valor sin haber guardado ninguno.");
        }
        tope--;
        int valor = pila[tope];
        pila[tope] = 0;
        return valor;
    }

    public boolean pilaVacia() {
        return tope == 0;
    }

    public boolean pilaLlena() {
        return tope == TAMANO_PILA;
    }

    /** Cantidad de elementos en la pila. */
    public int getTope() {
        return tope;
    }

    /** Copia de los valores que hay en la pila, del fondo al tope. */
    public int[] getValoresPila() {
        int[] copia = new int[tope];
        System.arraycopy(pila, 0, copia, 0, tope);
        return copia;
    }

    // ------------------------------------------------------------------
    // Informacion contable
    // ------------------------------------------------------------------

    /**
     * Registra la hora de inicio solo la primera vez que el proceso entra a la CPU.
     * Las siguientes llamadas no cambian la hora ya guardada.
     */
    public void registrarInicio(LocalTime hora) {
        if (horaInicio == null && hora != null) {
            horaInicio = hora;
        }
    }

    /** Suma segundos al tiempo de CPU empleado por el proceso (1 por cada tick). */
    public void sumarTiempoEmpleado(int segundos) {
        if (segundos > 0) {
            tiempoEmpleado += segundos;
        }
    }

    // ------------------------------------------------------------------
    // Archivos abiertos
    // ------------------------------------------------------------------

    /** Registra un archivo como abierto por el proceso (si ya estaba abierto no se repite). */
    public void abrirArchivo(String nombre) {
        if (nombre != null && !nombre.trim().isEmpty() && !archivosAbiertos.contains(nombre.trim())) {
            archivosAbiertos.add(nombre.trim());
        }
    }

    /** Quita un archivo de la lista de abiertos. */
    public void cerrarArchivo(String nombre) {
        if (nombre != null) {
            archivosAbiertos.remove(nombre.trim());
        }
    }

    public boolean tieneArchivoAbierto(String nombre) {
        return nombre != null && archivosAbiertos.contains(nombre.trim());
    }

    /** Lista de archivos abiertos (solo lectura). */
    public List<String> getArchivosAbiertos() {
        return Collections.unmodifiableList(archivosAbiertos);
    }

    // ------------------------------------------------------------------
    // Getters / Setters
    // ------------------------------------------------------------------

    public int getPid() {
        return pid;
    }

    public String getNombrePrograma() {
        return nombrePrograma;
    }

    public EstadoProceso getEstado() {
        return estado;
    }

    public void setEstado(EstadoProceso estado) {
        this.estado = estado;
    }

    public int getPrioridad() {
        return prioridad;
    }

    public void setPrioridad(int prioridad) {
        this.prioridad = prioridad;
    }

    public int getPosicionBCP() {
        return posicionBCP;
    }

    public void setPosicionBCP(int posicionBCP) {
        this.posicionBCP = posicionBCP;
    }

    public int getPc() {
        return pc;
    }

    public void setPc(int pc) {
        this.pc = pc;
    }

    public String getIr() {
        return ir;
    }

    public void setIr(String ir) {
        this.ir = ir;
    }

    public int getAc() {
        return ac;
    }

    public void setAc(int ac) {
        this.ac = ac;
    }

    public boolean isFlagIgual() {
        return flagIgual;
    }

    public void setFlagIgual(boolean flagIgual) {
        this.flagIgual = flagIgual;
    }

    public String getCpuAsignada() {
        return cpuAsignada;
    }

    public void setCpuAsignada(String cpuAsignada) {
        this.cpuAsignada = (cpuAsignada == null || cpuAsignada.trim().isEmpty()) ? SIN_VALOR : cpuAsignada.trim();
    }

    /** Hora de inicio, o null si el proceso aun no se ha ejecutado. */
    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public int getTiempoEmpleado() {
        return tiempoEmpleado;
    }

    public int getInstruccionesEjecutadas() {
        return instruccionesEjecutadas;
    }

    /** Direccion de memoria del siguiente BCP, o SIN_ENLACE (-1) si es el ultimo. */
    public int getEnlaceSiguiente() {
        return enlaceSiguiente;
    }

    public void setEnlaceSiguiente(int enlaceSiguiente) {
        this.enlaceSiguiente = enlaceSiguiente;
    }

    public boolean tieneSiguiente() {
        return enlaceSiguiente != SIN_ENLACE;
    }

    /** Base: primera posicion de memoria del programa (igual a getLimiteInferior()). */
    public int getBase() {
        return limiteInferior;
    }

    /** Alcance: cantidad de posiciones que ocupa el programa en memoria. */
    public int getAlcance() {
        return limiteSuperior - limiteInferior + 1;
    }

    public int getLimiteInferior() {
        return limiteInferior;
    }

    public int getLimiteSuperior() {
        return limiteSuperior;
    }

    // ------------------------------------------------------------------
    // Representacion del BCP en memoria
    // ------------------------------------------------------------------

    /**
     * Convierte el BCP en el arreglo de valores (texto) que se escribe en memoria,
     * en el orden definido por las constantes CAMPO_*. Siempre devuelve TAMANO_EN_MEMORIA valores.
     */
    public String[] aValoresDeMemoria() {
        String[] valores = new String[TAMANO_EN_MEMORIA];

        valores[CAMPO_PID_PROGRAMA] = pid + SEPARADOR + nombrePrograma;
        valores[CAMPO_ESTADO_PRIORIDAD] = estado.name() + SEPARADOR + prioridad;
        valores[CAMPO_PC] = String.valueOf(pc);
        valores[CAMPO_IR] = ir.isEmpty() ? SIN_VALOR : ir;
        valores[CAMPO_AC] = String.valueOf(ac);
        valores[CAMPO_REGISTROS_GENERALES] = unir(
                registros.get(Registro.AX), registros.get(Registro.BX),
                registros.get(Registro.CX), registros.get(Registro.DX));
        valores[CAMPO_AH_AL_BANDERA] = unir(
                registros.get(Registro.AH), registros.get(Registro.AL),
                flagIgual ? "=" : "!=");
        valores[CAMPO_PILA] = textoPila();
        valores[CAMPO_BASE_ALCANCE] = unir(getBase(), getAlcance());
        valores[CAMPO_CONTABLE] = unir(
                cpuAsignada,
                horaInicio == null ? "--:--:--" : horaInicio.format(FORMATO_HORA),
                tiempoEmpleado + "s");
        valores[CAMPO_ARCHIVOS] = archivosAbiertos.isEmpty() ? SIN_VALOR : String.join(", ", archivosAbiertos);
        valores[CAMPO_ENLACE] = String.valueOf(enlaceSiguiente);

        return valores;
    }

    /** Texto de la pila para memoria e interfaz: "[3, 7] (2)", o "[] (0)" si esta vacia. */
    public String textoPila() {
        StringBuilder texto = new StringBuilder("[");
        for (int i = 0; i < tope; i++) {
            if (i > 0) {
                texto.append(", ");
            }
            texto.append(pila[i]);
        }
        return texto.append("] (").append(tope).append(")").toString();
    }

    /** Une varios datos con el separador " / ". */
    private static String unir(Object... datos) {
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < datos.length; i++) {
            if (i > 0) {
                texto.append(SEPARADOR);
            }
            texto.append(datos[i]);
        }
        return texto.toString();
    }

    /**
     * Devuelve un texto legible para un campo del BCP a partir del valor que hay en memoria.
     * Lo usa la interfaz para mostrar el contenido del Kernel.
     * @param campo           desplazamiento del campo (0 a TAMANO_EN_MEMORIA - 1).
     * @param valorEnMemoria  valor (texto) leido de la posicion de memoria.
     */
    public static String describirCampo(int campo, String valorEnMemoria) {
        return "BCP." + NOMBRES_CAMPOS[campo] + " = " + valorEnMemoria;
    }

    @Override
    public String toString() {
        return String.format("BCP[pid=%d, programa=%s, estado=%s, PC=%d, IR=%s, AC=%d, AX=%d, BX=%d, CX=%d, DX=%d, "
                + "AH=%d, AL=%d, pila=%s, base=%d, alcance=%d, enlace=%d]",
                pid, nombrePrograma, estado, pc, ir, ac,
                registros.get(Registro.AX), registros.get(Registro.BX), registros.get(Registro.CX),
                registros.get(Registro.DX), registros.get(Registro.AH), registros.get(Registro.AL),
                textoPila(), getBase(), getAlcance(), enlaceSiguiente);
    }
}
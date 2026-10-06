package minipc;

import java.util.EnumMap;
import java.util.Map;

/**
 * Bloque de Control de Proceso (BCP / PCB).
 *
 * Guarda todo el estado necesario para pausar y retomar un proceso en la Mini PC:
 *   - Identificacion y estado del proceso.
 *   - COPIA de los registros de la CPU (contexto): PC, IR, AC, AX, BX, CX, DX.
 *     Los registros "reales" estan en la clase CPU; aqui solo se guarda su valor
 *     cuando el proceso sale de la CPU, para poder restaurarlo despues.
 *   - Limites de memoria asignados al proceso (protección).
 *
 * El BCP tambien se almacena en el espacio de Kernel de la memoria (a partir de la
 * posicion indicada en posicionBCP, por defecto la 0). 
 *
 * Cada campo se guarda en memoria como texto: los numeros con su valor decimal (ej: "-8"),
 * el estado con su nombre (ej: "LISTO") y el IR con la instruccion en ensamblador (ej: "MOV AX, 5").
 * @author Natalia Granados Rosales
 */
public class BCP {

    // ------------------------------------------------------------------
    // Distribucion del BCP dentro de la memoria (desplazamiento de cada campo)
    // ------------------------------------------------------------------
    public static final int CAMPO_PID = 0;
    public static final int CAMPO_ESTADO = 1;
    public static final int CAMPO_PRIORIDAD = 2;
    public static final int CAMPO_PC = 3;
    public static final int CAMPO_IR = 4;
    public static final int CAMPO_AC = 5;
    public static final int CAMPO_AX = 6;
    public static final int CAMPO_BX = 7;
    public static final int CAMPO_CX = 8;
    public static final int CAMPO_DX = 9;
    public static final int CAMPO_BASE = 10;
    public static final int CAMPO_LIMITE = 11;

    /**
     * Cantidad de posiciones de memoria que ocupa un BCP en el Kernel.
     * Con la division 25/75, una memoria de 256 deja 64 posiciones de Kernel:
     * 4 de encabezado del SO + 5 BCP x 12 = 64.
     */
    public static final int TAMANO_EN_MEMORIA = 12;

    /** Nombre de cada campo, en el mismo orden en que se guardan en memoria. */
    public static final String[] NOMBRES_CAMPOS = {
        "PID", "Estado", "Prioridad", "PC", "IR", "AC", "AX", "BX", "CX", "DX",
        "Base", "Limite"
    };

    private final int pid;
    private EstadoProceso estado;

    // Campos informativos adicionales para mostrar en la interfaz
    private int prioridad;
    private int posicionBCP;

    // Contador de programa: posición de memoria de la proxima instruccion a leer.
    private int pc;

    // Registro de instruccion: la instruccion en ensamblador que se leyo de memoria (ej: "MOV AX, 5").
    private String ir;

    // Acumulador: almacenamiento temporal para operaciones (valor real, con signo).
    private int ac;

    // Registros de proposito general (valor real, con signo).
    private final Map<Registro, Integer> registros;

    private final int limiteInferior;
    private final int limiteSuperior;

    private int instruccionesEjecutadas;

    public BCP(int pid, int limiteInferior, int limiteSuperior) {
        this.pid = pid;
        this.limiteInferior = limiteInferior;
        this.limiteSuperior = limiteSuperior;

        this.estado = EstadoProceso.NUEVO;
        this.pc = limiteInferior;
        this.ir = ""; // aún no se ha leído ninguna instrucción
        this.ac = 0;
        this.instruccionesEjecutadas = 0;
        this.prioridad = 1;
        this.posicionBCP = 0;

        this.registros = new EnumMap<>(Registro.class);
        for (Registro registro : Registro.values()) {
            registros.put(registro, 0);
        }
    }

    // Operaciones de uso

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
    // Getters / Setters
    // ------------------------------------------------------------------

    public int getPid() {
        return pid;
    }

    public EstadoProceso getEstado() {
        return estado;
    }

    public void setEstado(EstadoProceso estado) {
        this.estado = estado;
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

    public int getLimiteInferior() {
        return limiteInferior;
    }

    public int getLimiteSuperior() {
        return limiteSuperior;
    }

    public int getInstruccionesEjecutadas() {
        return instruccionesEjecutadas;
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

    // ------------------------------------------------------------------
    // Representacion del BCP en memoria
    // ------------------------------------------------------------------

    /**
     * Convierte el BCP en el arreglo de valores (texto) que se escribe en memoria,
     * en el orden definido por las constantes CAMPO_*.
     */
    public String[] aValoresDeMemoria() {
        String[] valores = new String[TAMANO_EN_MEMORIA];
        valores[CAMPO_PID] = String.valueOf(pid);
        valores[CAMPO_ESTADO] = estado.name();
        valores[CAMPO_PRIORIDAD] = String.valueOf(prioridad);
        valores[CAMPO_PC] = String.valueOf(pc);
        valores[CAMPO_IR] = ir.isEmpty() ? "-" : ir;
        valores[CAMPO_AC] = String.valueOf(ac);
        valores[CAMPO_AX] = String.valueOf(registros.get(Registro.AX));
        valores[CAMPO_BX] = String.valueOf(registros.get(Registro.BX));
        valores[CAMPO_CX] = String.valueOf(registros.get(Registro.CX));
        valores[CAMPO_DX] = String.valueOf(registros.get(Registro.DX));
        valores[CAMPO_BASE] = String.valueOf(limiteInferior);
        valores[CAMPO_LIMITE] = String.valueOf(limiteSuperior);
        return valores;
    }

    /**
     * Devuelve un texto legible para un campo del BCP a partir del valor que hay en memoria.
     * Lo usa la interfaz para mostrar el contenido del Kernel (ej: "BCP.AC = -8").
     * @param campo           desplazamiento del campo (0 a TAMANO_EN_MEMORIA - 1).
     * @param valorEnMemoria  valor (texto) leido de la posicion de memoria.
     */
    public static String describirCampo(int campo, String valorEnMemoria) {
        return "BCP." + NOMBRES_CAMPOS[campo] + " = " + valorEnMemoria;
    }

    @Override
    public String toString() {
        return String.format( "BCP[pid=%d, estado=%s, PC=%d, IR=%s, AC=%d, AX=%d, BX=%d, CX=%d, DX=%d, instrucciones=%d]", pid, estado, pc, ir, ac, registros.get(Registro.AX), registros.get(Registro.BX), registros.get(Registro.CX), registros.get(Registro.DX), instruccionesEjecutadas);
    }
}
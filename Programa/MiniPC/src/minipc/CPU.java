package minipc;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Representa el procesador de la Mini PC.
 *
 * La CPU contiene sus PROPIOS registros fisicos:
 *   - PC : contador de programa (posicion de la proxima instruccion).
 *   - IR : registro de instruccion (la instruccion en ensamblador leida de memoria).
 *   - AC : acumulador.
 *   - AX, BX, CX, DX : registros de proposito general.
 *
 * Todas las instrucciones se ejecutan sobre estos registros. El BCP solo guarda una COPIA
 * de ellos (el contexto del proceso), que se usa para pausar y retomar la ejecucion:
 *   - cargarContexto(bcp) : copia los registros guardados en el BCP hacia la CPU.
 *   - guardarContexto()   : copia los registros de la CPU hacia el BCP y escribe el BCP en
 *   el espacio de Kernel de la memoria.
 *
 * Ciclo de ejecucion: FETCH -> DECODE -> EXECUTE.
 * Soporta ejecutar un solo paso (boton "Paso a paso") o el programa completo (boton "Ejecutar").
 * @author Natalia Granados Rosales
 */
public class CPU {

    private final Memoria memoria;

    /** Se usa en la etapa DECODE para interpretar el texto de la instruccion leida de memoria. */
    private final ProcesadorInstrucciones decodificador = new ProcesadorInstrucciones();

    /** Contador de programa: posicion de memoria de la proxima instruccion a leer. */
    private int pc;

    /** Registro de instruccion: la instruccion (texto en ensamblador) leida de memoria. */
    private String ir;

    /** Acumulador. */
    private int ac;

    /** Registros de proposito general AX, BX, CX, DX. */
    private final Map<Registro, Integer> registros;

    /** Proceso cuyo contexto esta cargado actualmente en la CPU (null si no hay ninguno). */
    private BCP procesoActual;

    public CPU(Memoria memoria) {
        this.memoria = memoria;
        this.registros = new EnumMap<>(Registro.class);
        reiniciarRegistros();
    }

    /** Pone todos los registros de la CPU en su valor inicial. */
    public final void reiniciarRegistros() {
        pc = 0;
        ir = "";
        ac = 0;
        for (Registro registro : Registro.values()) {
            registros.put(registro, 0);
        }
        procesoActual = null;
    }

    // ------------------------------------------------------------------
    // Contexto (BCP <-> CPU)
    // ------------------------------------------------------------------

    /** Copia los registros guardados en el BCP hacia los registros de la CPU. */
    public void cargarContexto(BCP bcp) {
        pc = bcp.getPc();
        ir = bcp.getIr();
        ac = bcp.getAc();
        for (Registro registro : Registro.values()) {
            registros.put(registro, bcp.obtenerValorRegistro(registro));
        }
        procesoActual = bcp;
    }

    /**
     * Copia los registros de la CPU hacia el BCP del proceso actual y luego escribe
     * el BCP actualizado en el espacio de Kernel de la memoria.
     */
    public void guardarContexto() {
        if (procesoActual == null) {
            return;
        }
        procesoActual.setPc(pc);
        procesoActual.setIr(ir);
        procesoActual.setAc(ac);
        for (Registro registro : Registro.values()) {
            procesoActual.asignarValorRegistro(registro, registros.get(registro));
        }
        memoria.guardarBCP(procesoActual);
    }

    // ------------------------------------------------------------------
    // FETCH
    // ------------------------------------------------------------------

    /** Lee de Memoria la instruccion apuntada por el PC y la guarda en el IR. */
    private void fetch() {
        if (!memoria.esPosicionValida(pc)) {
            throw new IllegalStateException("El PC (" + pc + ") quedó fuera del rango válido de memoria.");
        }
        ir = memoria.leer(pc);
        if (ir.isEmpty()) {
            throw new IllegalStateException("La posición " + pc + " de memoria está vacía: no hay instrucción para ejecutar.");
        }
    }

    // ------------------------------------------------------------------
    // DECODE
    // ------------------------------------------------------------------

    /**
     * Interpreta el texto que quedo en el IR (ej: "MOV AX, 5") y reconstruye la Instruccion
     * (operador, registro y, si aplica, el valor inmediato).
     * El numero de linea original no se conoce en esta etapa, por lo que se usa -1.
     */
    private Instruccion decode() {
        Instruccion instruccion = decodificador.procesarLinea(ir, -1);
        if (!instruccion.isValida()) {
            throw new IllegalStateException("Instrucción inválida en la posición " + pc + " de memoria: " + instruccion.getMensajeError());
        }
        return instruccion;
    }

    // ------------------------------------------------------------------
    // EXECUTE
    // ------------------------------------------------------------------

    /** Aplica la operacion decodificada sobre los registros de la CPU (AC y AX-DX). */
    private void execute(Instruccion instruccion) {
        Registro registro = instruccion.getRegistro();

        switch (instruccion.getOperador()) {
            case MOV:
                registros.put(registro, instruccion.getValor());
                break;

            case LOAD:
                ac = registros.get(registro);
                break;

            case STORE:
                registros.put(registro, ac);
                break;

            case ADD:
                ac = sumarConControl(ac, registros.get(registro));
                break;

            case SUB:
                ac = restarConControl(ac, registros.get(registro));
                break;

            default:
                throw new IllegalStateException("Operador no soportado: " + instruccion.getOperador());
        }
    }

    /** Suma dos valores detectando el desbordamiento del tipo entero. */
    private int sumarConControl(int a, int b) {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException ex) {
            throw new ArithmeticException("Desbordamiento: el resultado de " + a + " + " + b + " excede el rango permitido.");
        }
    }

    /** Resta dos valores detectando el desbordamiento del tipo entero. */
    private int restarConControl(int a, int b) {
        try {
            return Math.subtractExact(a, b);
        } catch (ArithmeticException ex) {
            throw new ArithmeticException("Desbordamiento: el resultado de " + a + " - " + b + " excede el rango permitido.");
        }
    }

    // ------------------------------------------------------------------
    // Control de ejecucion (paso a paso / completo)
    // ------------------------------------------------------------------

    /**
     * Ejecuta un solo paso del ciclo fetch-decode-execute sobre el proceso indicado.
     * Si el contexto del proceso no esta en la CPU, primero se carga desde su BCP.
     * Al terminar el paso, el contexto se guarda en el BCP y el BCP se escribe en el Kernel.
     * @return la Instruccion que se acaba de ejecutar, o null si el proceso ya no tenia instrucciones pendientes.
     */
    public Instruccion ejecutarUnPaso(BCP bcp) {
        if (!bcp.tieneInstruccionesPendientes()) {
            bcp.setEstado(EstadoProceso.TERMINADO);
            memoria.guardarBCP(bcp);
            return null;
        }

        if (procesoActual != bcp) {
            cargarContexto(bcp);
        }

        bcp.setEstado(EstadoProceso.EJECUTANDO);

        fetch();
        Instruccion instruccion = decode();
        execute(instruccion);
        pc++;

        bcp.registrarInstruccionEjecutada();
        guardarContexto(); // CPU -> BCP -> memoria (Kernel)

        if (!bcp.tieneInstruccionesPendientes()) {
            bcp.setEstado(EstadoProceso.TERMINADO);
            memoria.guardarBCP(bcp);
        }
        return instruccion;
    }

    /**
     * Ejecuta el programa completo de corrido, paso por paso internamente, hasta que el proceso termine.
     * @return la lista de instrucciones ejecutadas, en orden.
     */
    public List<Instruccion> ejecutarTodo(BCP bcp) {
        List<Instruccion> ejecutadas = new ArrayList<>();
        Instruccion instruccion;
        while ((instruccion = ejecutarUnPaso(bcp)) != null) {
            ejecutadas.add(instruccion);
        }
        return ejecutadas;
    }

    // ------------------------------------------------------------------
    // Getters de los registros (para la interfaz)
    // ------------------------------------------------------------------

    public int getPc() {
        return pc;
    }

    public String getIr() {
        return ir;
    }

    public int getAc() {
        return ac;
    }

    public int getRegistro(Registro registro) {
        return registros.get(registro);
    }

    public BCP getProcesoActual() {
        return procesoActual;
    }
}
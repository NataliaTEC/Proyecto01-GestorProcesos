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

    /** Registros de proposito general AX, BX, CX, DX, AH y AL. */
    private final Map<Registro, Integer> registros;

    /** Bandera de igualdad: la activa CMP cuando ambos registros son iguales; la consultan JE y JNE. */
    private boolean flagIgual;

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
        flagIgual = false;
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
        flagIgual = bcp.isFlagIgual();
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
        procesoActual.setFlagIgual(flagIgual);
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
     * (operador y operandos), usando el mismo procesador que valido el archivo.
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

    /**
     * Aplica la operacion decodificada sobre los registros de la CPU.
     * @return true si la instruccion cambio el PC (un salto tomado), para no avanzarlo despues.
     */
    private boolean execute(Instruccion instruccion) {
        switch (instruccion.getOperador()) {
            case MOV: {
                Registro destino = instruccion.getRegistro(0);
                int valor = instruccion.esRegistro(1)
                        ? registros.get(instruccion.getRegistro(1))   // MOV registro, registro
                        : instruccion.getNumero(1);                    // MOV registro, numero
                registros.put(destino, valor);
                return false;
            }

            case LOAD:
                ac = registros.get(instruccion.getRegistro(0));
                return false;

            case STORE:
                registros.put(instruccion.getRegistro(0), ac);
                return false;

            case ADD:
                ac = sumarConControl(ac, registros.get(instruccion.getRegistro(0)));
                return false;

            case SUB:
                ac = restarConControl(ac, registros.get(instruccion.getRegistro(0)));
                return false;

            case INC:
            case DEC: {
                int cambio = instruccion.getOperador() == Operador.INC ? 1 : -1;
                if (instruccion.getCantidadOperandos() == 0) {
                    ac = sumarConControl(ac, cambio);               // INC / DEC sin operando: AC
                } else {
                    Registro registro = instruccion.getRegistro(0);
                    registros.put(registro, sumarConControl(registros.get(registro), cambio));
                }
                return false;
            }

            case SWAP: {
                Registro primero = instruccion.getRegistro(0);
                Registro segundo = instruccion.getRegistro(1);
                int temporal = registros.get(primero);
                registros.put(primero, registros.get(segundo));
                registros.put(segundo, temporal);
                return false;
            }

            case CMP:
                flagIgual = registros.get(instruccion.getRegistro(0)).equals(registros.get(instruccion.getRegistro(1)));
                return false;

            case JMP:
                return saltar(instruccion);

            case JE:
                return flagIgual && saltar(instruccion);

            case JNE:
                return !flagIgual && saltar(instruccion);

            case INT:
                if (instruccion.getInterrupcion() == Interrupcion.FIN_PROGRAMA) {
                    procesoActual.setEstado(EstadoProceso.TERMINADO); // INT 20H
                    return false;
                }
                throw new UnsupportedOperationException("INT " + instruccion.getInterrupcion().getCodigo()
                        + " está validada, pero su ejecución (pantalla, teclado y archivos) se implementa en la Fase 7.");

            case PARAM:
            case PUSH:
            case POP:
                throw new UnsupportedOperationException(instruccion.getOperador()
                        + " está validada, pero su ejecución (pila del proceso) se implementa en la Fase 6.");

            default:
                throw new IllegalStateException("Operador no soportado: " + instruccion.getOperador());
        }
    }

    /**
     * Mueve el PC segun el desplazamiento, contado desde la propia instruccion de salto.
     * Proteccion: el destino debe quedar dentro del espacio del proceso (Base..Limite);
     * si no, es un desbordamiento y se detiene el proceso.
     * @return true (el PC ya quedo en el destino).
     */
    private boolean saltar(Instruccion instruccion) {
        long destino = (long) pc + instruccion.getDesplazamiento();
        if (procesoActual != null
                && (destino < procesoActual.getLimiteInferior() || destino > procesoActual.getLimiteSuperior())) {
            throw new IllegalStateException("Desbordamiento: " + instruccion.getTextoNormalizado() + " en la posición "
                    + pc + " saltaría a la posición " + destino + ", fuera del proceso ("
                    + procesoActual.getLimiteInferior() + "-" + procesoActual.getLimiteSuperior() + ").");
        }
        pc = (int) destino;
        return true;
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
        boolean salto = execute(instruccion);
        if (!salto) {
            pc++;
        }

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

    public boolean isFlagIgual() {
        return flagIgual;
    }

    public BCP getProcesoActual() {
        return procesoActual;
    }
}
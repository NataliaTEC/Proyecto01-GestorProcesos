# Gestor de Procesos — Proyecto 1

**Estudiante:** Natalia Granados Rosales
**Carné:** 2021144286
**Curso:** Principios de Sistemas Operativos
**Estado del proyecto:** 2
**Enlace del video:**

Simulador gráfico, escrito en Java (Swing), de un gestor de procesos sobre una Mini PC con una sola CPU. Carga varios programas escritos en un mini lenguaje ensamblador, los valida, los guarda en un disco con índice de archivos y los copia a la memoria principal, donde cada proceso tiene su Bloque de Control de Proceso (BCP) en el espacio del sistema operativo. Los BCP de los procesos listos forman una lista enlazada dentro de la propia memoria.

Este proyecto es la continuación de la Tarea Programada 1 (Mini PC). Respecto a la tarea, se eliminó la traducción a binario: la memoria y el disco guardan las instrucciones y los datos del BCP como texto.

---

## Tabla de contenido

- [Características](#características)
- [Cómo ejecutar el proyecto](#cómo-ejecutar-el-proyecto)
- [Configuración](#configuración)
- [Uso de la interfaz](#uso-de-la-interfaz)
- [Lenguaje ensamblador soportado](#lenguaje-ensamblador-soportado)
- [Modelo de memoria principal](#modelo-de-memoria-principal)
- [Bloque de Control de Proceso (BCP)](#bloque-de-control-de-proceso-bcp)
- [Almacenamiento secundario (disco)](#almacenamiento-secundario-disco)
- [Protección y seguridad](#protección-y-seguridad)
- [Arquitectura del proyecto](#arquitectura-del-proyecto)
- [Estructura de carpetas](#estructura-de-carpetas)
- [Ejemplo de programa](#ejemplo-de-programa)
- [Objetivos alcanzados y no alcanzados](#objetivos-alcanzados-y-no-alcanzados)

---

## Características

- Carga de **varios archivos `.asm` a la vez**. Cada archivo se valida por separado: los válidos se guardan en el disco y los inválidos se rechazan con la lista de errores (nombre del archivo, línea y motivo).
- Validación completa de sintaxis: operador, cantidad y tipo de operandos, comas consecutivas, iniciales o finales, registros válidos, códigos de interrupción y límite de parámetros.
- **Configuración externa** (`config.properties`) del tamaño de la memoria principal y del disco, editable desde una ventana con validación y valores por defecto.
- Memoria principal dividida en **25 % Kernel / 75 % Usuario**. El Kernel guarda un encabezado del SO y hasta **5 BCP**.
- Asignación de memoria de usuario **contigua por primer ajuste**.
- **BCP completo** de 12 posiciones en memoria, con la cola de listos implementada como **lista enlazada por direcciones de memoria**.
- Modelo de **7 estados** de proceso.
- Disco con **índice de archivos** al inicio (nombre, dirección y tamaño), área de archivos y área de memoria virtual.
- CPU con ciclo **fetch → decode → execute**, ejecución paso a paso o automática, y guardado del contexto en el BCP.
- Interfaz gráfica con tablas de instrucciones, memoria y disco (con colores por zona) y un panel con el BCP del proceso en ejecución.

---

## Cómo ejecutar el proyecto

### Desde NetBeans

1. Abre el proyecto en NetBeans.
2. Click derecho sobre `minipc/MainMiniPC.java` → **Run File** (o ejecuta el proyecto si `MainMiniPC` es la clase principal).
3. Se abrirá la ventana principal de la aplicación.

### Desde terminal

```bash
cd src
javac -encoding UTF-8 -d ../out $(find . -name "*.java")
java -cp ../out minipc.MainMiniPC
```

El archivo `config.properties` se lee y se guarda en la carpeta desde donde se ejecuta la aplicación.

---

## Configuración

Los tamaños configurables no están en el código: se leen de `config.properties`. Si el archivo no existe o tiene valores inválidos, se usan los valores por defecto y la ventana de configuración muestra el aviso correspondiente.

```properties
memoria.principal=256
memoria.secundaria=512
```

| Parámetro | Valor | Configurable |
|---|---|:---:|
| Memoria principal | 256 por defecto (máximo 4096) | Sí |
| Disco (almacenamiento secundario) | 512 por defecto (máximo 8192) | Sí |
| División de la memoria | 25 % Kernel / 75 % Usuario | No |
| Índice de archivos | 5 % inicial del disco, mínimo 10 entradas | No |
| Memoria virtual | 64 posiciones al final del disco | No |
| Procesos activos simultáneos | 5 | No |

Reglas de validación:

- El 25 % de la memoria principal debe alcanzar para el encabezado del SO (4 posiciones) y los 5 BCP (12 posiciones cada uno). Con la configuración actual, el **mínimo es 256**.
- El disco debe alcanzar para el índice, un mínimo de 32 posiciones de archivos y la memoria virtual. El **mínimo es 106**.

---

## Uso de la interfaz

1. Pulsa **Cargar archivos (.asm)**. La primera vez (o después de **Limpiar**) se abre la ventana de configuración de memoria; al aceptarla se crean la memoria principal y el disco con esos tamaños.
2. Selecciona uno o varios archivos `.asm`. Se muestra un resumen con los archivos guardados en el disco (y en qué posiciones), los rechazados con sus errores, las advertencias y en qué parte de la memoria quedó cada proceso.
3. Cada programa válido se copia **del disco** a la memoria si hay una ranura de BCP libre y un espacio contiguo donde quepa. Si no, queda guardado en el disco.
4. El primer proceso de la cola de listos pasa a la CPU. Su programa aparece en la tabla de **Instrucciones** y sus datos en el panel **BCP actual**.
5. **Paso a paso** ejecuta una instrucción por clic; **Ejecutar** corre el proceso completo de forma animada.
6. Al terminar un proceso, se liberan su memoria y su ranura de BCP, y pasa a la CPU el siguiente de la cola.
7. **Limpiar** reinicia la memoria, el disco y los procesos.

En la tabla de memoria, el encabezado del SO se muestra en verde y las ranuras de BCP ocupadas en amarillo, con el nombre de cada campo. Al pasar el mouse sobre una fila se indica a qué proceso pertenece. En la tabla de disco se distinguen el índice, el área de archivos y la memoria virtual.

---

## Lenguaje ensamblador soportado

### Instrucciones

| Instrucción | Operandos válidos | Peso | Descripción |
|---|---|:---:|---|
| `LOAD` | registro | 2 | AC = registro |
| `STORE` | registro | 2 | registro = AC |
| `MOV` | registro, registro **o** registro, número | 1 | Copia un registro o un número al registro destino |
| `ADD` | registro | 3 | AC = AC + registro |
| `SUB` | registro | 3 | AC = AC − registro |
| `INC` | ninguno **o** registro | 1 | Incrementa en 1 el AC o el registro |
| `DEC` | ninguno **o** registro | 1 | Decrementa en 1 el AC o el registro |
| `SWAP` | registro, registro | 1 | Intercambia los valores de dos registros |
| `CMP` | registro, registro | 2 | Compara dos registros y activa la bandera de igualdad |
| `JMP` | desplazamiento (`+3`, `-2`) | 2 | Salto incondicional |
| `JE` | desplazamiento | 2 | Salta si la última comparación fue igual |
| `JNE` | desplazamiento | 2 | Salta si la última comparación fue distinta |
| `PARAM` | de 1 a 3 números | 3 | Guarda parámetros en la pila |
| `PUSH` | registro | 1 | Guarda el registro en la pila |
| `POP` | registro | 1 | Saca el último valor de la pila al registro |
| `INT` | `20H`, `10H`, `09H` o `21H` | según código | Interrupción |

### Interrupciones

| Código | Peso | Función |
|---|:---:|---|
| `20H` | 2 | Finaliza el programa |
| `10H` | 2 | Imprime en pantalla el valor de DX |
| `09H` | variable | Lee un valor del teclado (0-255) y lo guarda en DX |
| `21H` | 5 | Manejo de archivos según AH (crear, abrir, leer, escribir, eliminar); el nombre va en DX y el contenido en AL |

### Registros

`AX`, `BX`, `CX`, `DX`, `AH` y `AL` se pueden usar como operandos. `AC`, `PC` e `IR` son registros internos de la CPU y no se permiten como operandos.

### Reglas de sintaxis

- Las líneas vacías y las que empiezan con `;` o `#` se ignoran. Un comentario también puede ir al final de una línea, después de `;` (ej: `INC AX ; contador`).
- Los operandos se separan con una sola coma. Comas consecutivas, al inicio o al final generan error (ej: `MOV ,,, AX, 20`).
- Cada instrucción debe tener la cantidad y el tipo de operandos de la tabla.
- Entre todas las instrucciones `PARAM` de un programa no puede haber más de 3 parámetros.
- Advertencias (no impiden la carga): el programa no termina con `INT 20H`, o hay `PARAM` después de otras instrucciones.

### Convención de los saltos

El desplazamiento se cuenta desde la posición de la propia instrucción de salto. Por ejemplo, `JMP +2` en la posición 70 salta a la 72. Si el destino queda fuera del espacio del proceso (base a base + alcance − 1), se produce un error de desbordamiento.

---

## Modelo de memoria principal

Cada posición guarda texto: en el área de usuario, una instrucción completa (ej: `MOV AX, 5`); en el Kernel, los campos del encabezado y de los BCP.

Distribución con 256 posiciones:

| Posiciones | Contenido |
|---|---|
| 0 | Encabezado: dirección del primer BCP de la cola de listos (`-1` si está vacía) |
| 1 | Encabezado: dirección del último BCP de la cola de listos |
| 2 | Encabezado: cantidad de procesos activos |
| 3 | Encabezado: dirección del BCP en ejecución (`-1` si la CPU está libre) |
| 4–15, 16–27, 28–39, 40–51, 52–63 | Ranuras de los 5 BCP |
| 64–255 | Área de usuario (programas) |

- La ranura *i* empieza en `4 + i × 12`. Al crear un proceso se ocupa la primera ranura libre.
- Los programas se ubican en el área de usuario con **primer ajuste**: el primer hueco contiguo donde quepa el programa completo. Al terminar un proceso, su espacio se libera y puede reutilizarse.
- La **cola de listos** (FCFS) es una lista enlazada dentro de la memoria: el campo 0 del encabezado apunta al primer BCP, el campo "enlace" de cada BCP apunta a la dirección del siguiente, y el último tiene `-1`.

---

## Bloque de Control de Proceso (BCP)

El BCP ocupa 12 posiciones del Kernel. Los datos relacionados se agrupan en una misma posición, separados por ` / `:

| Pos. | Campo | Ejemplo |
|:---:|---|---|
| 0 | PID / programa | `2 / suma.asm` |
| 1 | Estado / prioridad | `LISTO / 1` |
| 2 | PC | `78` |
| 3 | IR | `ADD BX` |
| 4 | AC | `-8` |
| 5 | AX / BX / CX / DX | `0 / 5 / 0 / 2` |
| 6 | AH / AL / bandera CMP | `1 / 7 / =` |
| 7 | Pila (tope) | `[3, 7] (2)` |
| 8 | Base / alcance | `76 / 12` |
| 9 | CPU / inicio / tiempo empleado | `CPU1 / 10:32:05 / 14s` |
| 10 | Archivos abiertos | `datos.txt` o `-` |
| 11 | Enlace al siguiente BCP | `28` o `-1` |

Estados de un proceso: **Nuevo, Listo, Ejecutando, Bloqueado, Listo suspendido, Bloqueado suspendido y Terminado**.

---

## Almacenamiento secundario (disco)

| Zona | Contenido |
|---|---|
| Primeras posiciones (5 % del disco, mínimo 10) | **Índice**: una entrada por archivo, con nombre, dirección de inicio y tamaño |
| Zona central | Contenido de los archivos, una línea por posición |
| Últimas 64 posiciones | **Memoria virtual** |

Los programas se guardan en el disco al cargarse y desde ahí se copian a la memoria principal (no desde el archivo original). El índice ocupa el 5 % del disco porque, con un tamaño promedio de programa de unas 20 posiciones, permite registrar al menos tantos archivos como caben en el área de datos, para cualquier tamaño de disco.

---

## Protección y seguridad

Estrategia implementada hasta el momento:

- **Validación antes de cargar:** ningún archivo entra al disco ni a la memoria sin pasar la validación de sintaxis.
- **Separación Kernel / Usuario:** `escribirEnUsuario` y `escribirEnKernel` impiden escribir fuera del espacio correspondiente. Los BCP no pueden escribirse sobre el encabezado del SO ni fuera de las ranuras.
- **Base y alcance por proceso:** cada proceso conoce su espacio de memoria. Un salto cuyo destino queda fuera de ese espacio produce un error de desbordamiento.
- **Desbordamiento aritmético:** las sumas y restas que exceden el rango del tipo entero se reportan como error.
- **Desbordamiento de pila:** el BCP rechaza un sexto valor en la pila y un `POP` con la pila vacía.
- **Configuración validada:** no se acepta una memoria o un disco que no alcance para el SO, el índice y la memoria virtual.

---

## Arquitectura del proyecto

| Paquete | Clase | Responsabilidad |
|---|---|---|
| `minipc` | `MainMiniPC` | Punto de entrada; lanza la interfaz gráfica |
| `minipc.ensamblador` | `Operador` | Instrucciones, su peso y las formas de operandos válidas |
| | `Registro` | Registros usables como operandos (AX, BX, CX, DX, AH, AL) |
| | `TipoOperando` | Tipos de operando (registro, número, desplazamiento, interrupción) |
| | `Interrupcion` | Códigos de interrupción válidos y su peso |
| | `Instruccion` | Resultado del análisis de una línea: operador, operandos y posición en memoria |
| | `ProcesadorInstrucciones` | Valida y procesa el código ensamblador (línea por línea y el programa completo) |
| | `ResultadoAnalisis` | Instrucciones, errores y advertencias del análisis de un programa |
| `minipc.hardware` | `CPU` | Ciclo fetch → decode → execute; carga y guarda el contexto del BCP |
| | `Memoria` | Memoria principal (Kernel/Usuario) y encabezado del SO |
| | `Disco` | Almacenamiento secundario: índice, archivos y memoria virtual |
| | `EntradaIndice` | Entrada del índice del disco (nombre, inicio, tamaño) |
| `minipc.so` | `BCP` | Bloque de Control de Proceso |
| | `EstadoProceso` | Los 7 estados de un proceso |
| | `GestorMemoria` | Ranuras de BCP y asignación del área de usuario por primer ajuste |
| | `ColaListos` | Cola de listos FCFS enlazada en memoria |
| | `CargadorMemoria` | Copia un programa a memoria y crea su BCP |
| | `GestorArchivo` | Selección de archivos, validación, escritura y lectura en el disco |
| | `ResultadoCargaArchivo` | Resultado de cargar un archivo al disco |
| `minipc.config` | `Configuracion` | Lectura, validación y guardado de `config.properties` |
| `minipc.gui` | `VentanaMiniPC` | Ventana principal: botones, tablas y panel del BCP |
| | `DialogoConfiguracion` | Ventana de configuración de memoria y disco |
| | `EstiloUI` | Paleta de colores, tipografías, botones e íconos |

### Flujo general

```
archivos .asm
     │
     ▼
GestorArchivo + ProcesadorInstrucciones ──► validación de cada archivo
     │
     ▼
Disco ──► el programa se guarda y se registra en el índice
     │
     ▼
GestorMemoria ──► ranura de BCP libre + hueco contiguo (primer ajuste)
     │
     ▼
CargadorMemoria ──► copia el programa DESDE EL DISCO a memoria y escribe el BCP
     │
     ▼
ColaListos ──► el proceso entra al final de la cola (lista enlazada en memoria)
     │
     ▼
CPU ──► ejecuta el proceso y guarda su contexto en el BCP
     │
     ▼
VentanaMiniPC ──► muestra memoria, disco, instrucciones y BCP
```

---

## Estructura de carpetas

```
src/
└── minipc/
    ├── MainMiniPC.java
    ├── config/
    │   └── Configuracion.java
    ├── ensamblador/
    │   ├── Instruccion.java
    │   ├── Interrupcion.java
    │   ├── Operador.java
    │   ├── ProcesadorInstrucciones.java
    │   ├── Registro.java
    │   ├── ResultadoAnalisis.java
    │   └── TipoOperando.java
    ├── gui/
    │   ├── DialogoConfiguracion.java
    │   ├── EstiloUI.java
    │   └── VentanaMiniPC.java
    ├── hardware/
    │   ├── CPU.java
    │   ├── Disco.java
    │   ├── EntradaIndice.java
    │   └── Memoria.java
    └── so/
        ├── BCP.java
        ├── CargadorMemoria.java
        ├── ColaListos.java
        ├── EstadoProceso.java
        ├── GestorArchivo.java
        ├── GestorMemoria.java
        └── ResultadoCargaArchivo.java
```

---

## Ejemplo de programa

`suma.asm`:

```asm
MOV AX, 5
MOV BX, 3
LOAD AX
ADD BX          ; AC = 5 + 3
CMP AX, BX
JE +2           ; no salta: AX y BX son distintos
STORE CX        ; CX = 8
INT 20H
```

Con una memoria de 256 posiciones, si es el primer programa cargado se ubica en las posiciones 64-71 y su BCP en la ranura 4-15. Al ejecutarlo, el AC pasa por `0, 0, 5, 8`, y el programa termina con `AX = 5`, `BX = 3`, `CX = 8`.

---

## Objetivos alcanzados y no alcanzados

Estado según los rubros de la evaluación.

### Alcanzados

| Rubro | Detalle |
|---|---|
| Validar archivos | Validación completa de sintaxis por archivo, con errores por línea; los archivos inválidos se rechazan sin detener la carga de los demás |
| Memoria | Tamaño configurable desde archivo externo y ventana, división 25/75, encabezado del SO, 5 ranuras de BCP y asignación contigua por primer ajuste |
| BCP | Todos los campos (identificación, registros, pila, información contable, archivos abiertos, base/alcance y enlace) guardados en el Kernel; cola de listos enlazada por direcciones de memoria |
| Almacenamiento secundario | Disco con índice (nombre y dirección) en las primeras posiciones, carga de varios archivos y copia de los programas desde el disco a la memoria |

### Parcialmente alcanzados

| Rubro | Lo que funciona | Lo que falta |
|---|---|---|
| Ejecución de instrucciones | `MOV`, `LOAD`, `STORE`, `ADD`, `SUB`, `INC`, `DEC`, `SWAP`, `CMP`, `JMP`, `JE`, `JNE` e `INT 20H` | Ejecutar `PUSH`, `POP` y `PARAM`; respetar el peso de cada instrucción (hoy cada clic ejecuta una instrucción completa) |
| CPU | Ciclo fetch → decode → execute y guardado del contexto en el BCP | Ejecución por segundos (un tick por segundo de CPU) y reloj del sistema |
| Planificador y despachador | Cola de listos FCFS; al terminar un proceso pasa a la CPU el siguiente de la cola | Planificador de largo plazo (admitir procesos en espera y usar la memoria virtual) y despachador con cambio de contexto completo |
| Interrupciones | `INT 20H` finaliza el programa | `INT 10H`, `INT 09H` e `INT 21H` |
| Ejecución en ambos modos | Modo paso a paso y modo automático | Que ambos modos avancen por segundos y alternen entre procesos |
| Apariencia de la GUI | Tablas de instrucciones, memoria y disco con colores por zona; panel del BCP; ventana de configuración | Tabla de procesos y estados, pantalla, campo de teclado y reloj |
| Protección y seguridad | Validación previa, separación Kernel/Usuario, control de saltos, de pila y aritmético | Validar cada acceso de un proceso contra su base y alcance, y terminar solo el proceso que falla sin detener el simulador |

### No alcanzados

| Rubro | Pendiente |
|---|---|
| Teclado | Entrada de un valor de 0 a 255 para `INT 09H` |
| Pantalla | Salida de `INT 10H` |
| Estadísticas | Hora de inicio, hora de fin y duración de cada proceso |
| Diagrama de paquetes | Diagrama en PDF con la explicación del diseño |
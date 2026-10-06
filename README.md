# Mini PC — Tarea Programada 1

**Estudiante:** Natalia Granados Rosales
**Carné:** 2021144286
**Estado del proyecto:** 4,5
**Enlace del video:** https://youtu.be/HxnR_1OeJEI

Simulador gráfico de un mini computador (CPU + memoria) que ejecuta programas escritos en un lenguaje ensamblador simplificado, desarrollado para el curso **Principios de Sistemas Operativos**.

El programa carga un archivo `.asm`, lo traduce a binario, lo carga en memoria (con separación de espacio de Kernel y Usuario), y lo ejecuta paso a paso o de corrido, mostrando en tiempo real el estado del procesador (PC, IR, AC, registros) a través de una interfaz 100% gráfica.

---

## Tabla de contenido

- [Características](#características)
- [Cómo ejecutar el proyecto](#cómo-ejecutar-el-proyecto)
- [Uso de la interfaz](#uso-de-la-interfaz)
- [Lenguaje ensamblador soportado](#lenguaje-ensamblador-soportado)
- [Modelo de memoria](#modelo-de-memoria)
- [Arquitectura del proyecto](#arquitectura-del-proyecto)
- [Estructura de carpetas](#estructura-de-carpetas)
- [Ejemplo de programa](#ejemplo-de-programa)
- [Posibles mejoras futuras](#posibles-mejoras-futuras)

---

## Características

- Traductor de ensamblador a binario con validación de sintaxis (operadores, registros, rangos de valores).
- Selector gráfico de archivos `.asm` (`JFileChooser`).
- Memoria configurable en tamaño (mínimo 128 posiciones), dividida automáticamente en **25 % Kernel / 75 % Usuario**.
- Bloque de Control de Proceso (BCP) con PC, IR, AC, registros AX/BX/CX/DX, prioridad y límites de memoria asignados.
- CPU con ciclo **fetch → decode → execute**, con ejecución paso a paso o automática (animada).
- Detección de errores: sintaxis inválida, registros/operadores no reconocidos, valores fuera de rango y desbordamiento aritmético.
- Interfaz gráfica plana y minimalista (sin dependencias externas, solo Swing), con tablas de instrucciones/memoria y panel de estado del BCP en tiempo real.

---

## Cómo ejecutar el proyecto

### Desde NetBeans

1. Abre el proyecto en NetBeans (o crea uno nuevo tipo *Java Application* y copia los archivos de `src/` respetando la estructura de paquetes).
2. Verifica que todos los archivos tengan `package minipc;` (o `package minipc.gui;` los que están en esa subcarpeta).
3. Click derecho sobre `MainMiniPC.java` → **Run File**.
4. Se abrirá la ventana principal de la aplicación.

### Desde terminal

```bash
cd src
javac minipc/*.java minipc/gui/*.java
java minipc.MainMiniPC
```

---

## Uso de la interfaz

1. **(Opcional)** Ajusta el tamaño de memoria en el campo "Memoria total" (mínimo 128, se puede escribir directamente o usar las flechas, que suben/bajan de 4 en 4).
2. Pulsa **Cargar archivo (.asm)** y selecciona tu programa. Si hay errores de sintaxis, se muestran en una ventana emergente y no se carga nada a memoria.
3. Si el programa es válido, se llena la tabla de **Instrucciones** (mnemónico + binario), la tabla de **Memoria** (qué instrucción ocupa cada posición) y el panel de **BCP actual** con el estado inicial del proceso.
4. Usa **Paso a paso** para ejecutar una instrucción a la vez, observando cómo cambian el `PC`, `IR`, `AC` y los registros.
5. Usa **Ejecutar** para correr el programa completo de forma animada (una instrucción cada ~450 ms).
6. Usa **Limpiar** para reiniciar todo y cargar un nuevo programa.

En ambas tablas se resalta automáticamente la fila correspondiente a la próxima instrucción a ejecutar (según el `PC` actual).

---

## Lenguaje ensamblador soportado

### Operadores

| Mnemónico | Opcode (4 bits) | Requiere valor inmediato |
|-----------|:---------------:|:-------------------------:|
| `LOAD`    | `0001`           | No (trae el AC desde el registro) |
| `STORE`   | `0010`           | No (guarda el AC en el registro) |
| `MOV`     | `0011`           | Sí (`MOV registro, valor`) |
| `SUB`     | `0100`           | No (AC = AC − registro) |
| `ADD`     | `0101`           | No (AC = AC + registro) |

### Registros

| Registro | Código (4 bits) |
|----------|:----------------:|
| `AX`     | `0001` |
| `BX`     | `0010` |
| `CX`     | `0011` |
| `DX`     | `0100` |

### Sintaxis

```asm
MOV AX, 5      ; carga el valor 5 en AX
LOAD AX        ; AC = valor de AX
ADD BX         ; AC = AC + BX
SUB AX         ; AC = AC - AX
STORE AX       ; AX = AC
```

- Las líneas vacías y las que empiezan con `;` o `#` se ignoran (comentarios).
- Los valores inmediatos de `MOV` deben estar en el rango **-127 a 127** (formato de 8 bits: signo + 7 bits de magnitud).
- Cualquier suma/resta que produzca un resultado fuera de ese rango se reporta como error de desbordamiento durante la ejecución.

### Codificación binaria

Cada instrucción ocupa **una sola posición de memoria**, como una palabra de 16 bits:

```
[ 4 bits opcode ][ 4 bits registro ][ 8 bits valor (signo + magnitud) ]
```

Ejemplo: `MOV BX, -8` → `0011 0010 10001000`

---

## Modelo de memoria

- Tamaño configurable desde la interfaz (mínimo 128 posiciones).
- Se divide automáticamente en dos espacios:
  - **Kernel**: primer 25 % de las posiciones (reservado, protegido).
  - **Usuario**: 75 % restante, donde se cargan los programas.
- Cada posición almacena un entero (0–65535) que representa la palabra de 16 bits de una instrucción. La representación binaria (`0011 0010 10001000`) es solo una vista para la interfaz; internamente todo se maneja como enteros.
- Los métodos `escribirEnUsuario` / `escribirEnKernel` impiden que un espacio escriba fuera de su rango asignado (protección básica de memoria).

---

## Arquitectura del proyecto

El proyecto separa **lógica** (paquete `minipc`) de **presentación** (subpaquete `minipc.gui`):

| Clase | Responsabilidad |
|---|---|
| `Operador` | Enum de operadores (`LOAD`, `STORE`, `MOV`, `SUB`, `ADD`) y su opcode binario. |
| `Registro` | Enum de registros (`AX`, `BX`, `CX`, `DX`) y su código binario. |
| `Instruccion` | Representa el resultado del análisis de una línea: válida/inválida, operador, registro, valor, traducción binaria y posición en memoria. |
| `ProcesadorInstrucciones` | Valida, procesa y traduce el código ensamblador (línea por línea o el programa completo). |
| `GestorArchivo` | Abre el selector gráfico de archivos `.asm` y lee su contenido. |
| `Memoria` | Arreglo configurable, dividido en Kernel/Usuario, con utilidades de conversión entero ↔ binario. |
| `EstadoProceso` | Enum de estados de un proceso (`NUEVO`, `LISTO`, `EJECUTANDO`, `TERMINADO`). |
| `BCP` | Bloque de Control de Proceso: PID, estado, PC, IR, AC, registros, prioridad y límites de memoria. |
| `CargadorMemoria` | Escribe las instrucciones ya traducidas en el espacio de Usuario y crea el BCP inicial. |
| `CPU` | Ciclo fetch → decode → execute; soporta ejecución de un paso o del programa completo. |
| `MainMiniPC` | Punto de entrada; lanza la interfaz gráfica. |
| `gui/EstiloUI` | Paleta de colores, tipografías, botones con fondo pintado a mano e íconos vectoriales simples. |
| `gui/VentanaMiniPC` | Ventana principal: botones, tablas de instrucciones/memoria y panel del BCP. |

### Flujo general

```
archivo .asm
     │
     ▼
GestorArchivo ──► líneas de texto
     │
     ▼
ProcesadorInstrucciones ──► List<Instruccion> (validadas y traducidas a binario)
     │
     ▼
CargadorMemoria ──► escribe en Memoria + crea BCP (estado LISTO)
     │
     ▼
CPU.ejecutarUnPaso() / ejecutarTodo() ──► actualiza BCP en cada ciclo
     │
     ▼
VentanaMiniPC ──► refleja el estado del BCP y resalta la instrucción actual
```

---

## Estructura de carpetas

```
src/
└── minipc/
    ├── Operador.java
    ├── Registro.java
    ├── Instruccion.java
    ├── ProcesadorInstrucciones.java
    ├── GestorArchivo.java
    ├── Memoria.java
    ├── EstadoProceso.java
    ├── BCP.java
    ├── CargadorMemoria.java
    ├── CPU.java
    ├── MainMiniPC.java
    └── gui/
        ├── EstiloUI.java
        └── VentanaMiniPC.java
```

---

## Ejemplo de programa

`ejemplo.asm`:

```asm
MOV AX, 5
MOV BX, 3
LOAD AX
ADD BX
SUB AX
STORE AX
MOV BX, -8
```

Con una memoria de 256 posiciones (Kernel 0–63, Usuario 64–255), este programa se carga desde la posición 64 y produce el siguiente recorrido del acumulador: `0, 0, 5, 8, 3, 3, 3`, terminando con `AX = 3`, `BX = -8` y `PC = 71`.

---

package minipc;

import minipc.gui.VentanaMiniPC;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Punto de entrada de la aplicacion. Lanza la interfaz gráfica en el hilo de eventos de Swing (Event Dispatch Thread).
 *
 * @author Natalia Granados Rosales
 */
public class MainMiniPC {

    public static void main(String[] args) {
        // Look and feel nativo, para que la ventana se vea acorde al SO
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignorado) {
            // si falla, se usa el look and feel por defecto de Swing
        }

        SwingUtilities.invokeLater(() -> {
            VentanaMiniPC ventana = new VentanaMiniPC();
            ventana.setVisible(true);
        });
    }
}
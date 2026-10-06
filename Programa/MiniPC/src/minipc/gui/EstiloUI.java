package minipc.gui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Constantes de color/fuente y fabricas de componentes (botones, tablas)
 * para mantener un estilo plano, minimalista y consistente en toda la
 * interfaz de la Mini PC.
 */
public final class EstiloUI {

    private EstiloUI() {
        // clase de utilidades, no se instancia
    }

    // ------------------------------------------------------------------
    // Paleta de colores
    // ------------------------------------------------------------------
    public static final Color FONDO = new Color(0xF4, 0xF6, 0xFA);
    public static final Color PANEL = Color.WHITE;
    public static final Color BORDE = new Color(0xE3, 0xE7, 0xEE);

    public static final Color PRIMARIO = new Color(0x2F, 0x6F, 0xED);
    public static final Color PRIMARIO_HOVER = new Color(0x25, 0x5D, 0xC9);

    public static final Color PELIGRO = new Color(0xE0, 0x50, 0x50);
    public static final Color PELIGRO_HOVER = new Color(0xC7, 0x3C, 0x3C);

    public static final Color TEXTO = new Color(0x22, 0x2B, 0x38);
    public static final Color TEXTO_SECUNDARIO = new Color(0x7A, 0x85, 0x94);

    public static final Color ESTADO_LISTO = new Color(0x5B, 0x6B, 0x82);
    public static final Color ESTADO_EJECUTANDO = new Color(0xE0, 0x8E, 0x1D);
    public static final Color ESTADO_TERMINADO = new Color(0x21, 0x96, 0x53);

    // ------------------------------------------------------------------
    // Tipografias
    // ------------------------------------------------------------------
    public static final Font FUENTE_BASE = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FUENTE_TITULO = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font FUENTE_ETIQUETA = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font FUENTE_MONO = new Font("Consolas", Font.PLAIN, 13);
    public static final Font FUENTE_MONO_BOLD = new Font("Consolas", Font.BOLD, 13);

    // ------------------------------------------------------------------
    // Botones
    // ------------------------------------------------------------------

    /** Botón "solido": fondo de color pintado a mano, texto blanco (accion principal). */
    public static JButton crearBotonPrimario(String texto, Color color, Color colorHover) {
        return new BotonPlano(texto, color, colorHover, Color.WHITE, null);
    }

    /** Boton "de linea": fondo claro, borde de color (accion secundaria). */
    public static JButton crearBotonSecundario(String texto) {
        return new BotonPlano(texto, PANEL, FONDO, TEXTO, BORDE);
    }

    /**
     * Boton que pinta su propio fondo (en vez de dejarselo al Look and Feel del
     * sistema). Esto evita el problema tipico de Windows donde setBackground()
     * en un JButton normal es ignorado y el botón se ve "hueco".
     */
    private static class BotonPlano extends JButton {
        private final Color colorBase;
        private final Color colorHover;
        private final Color colorBorde; 
        private boolean sobreBoton = false;

        BotonPlano(String texto, Color colorBase, Color colorHover, Color colorTexto, Color colorBorde) {
            super(texto);
            this.colorBase = colorBase;
            this.colorHover = colorHover;
            this.colorBorde = colorBorde;

            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setForeground(colorTexto);
            setFont(FUENTE_TITULO);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    sobreBoton = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    sobreBoton = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

            Color relleno = isEnabled() ? (sobreBoton ? colorHover : colorBase) : new Color(0xE8, 0xE9, 0xED);
            g2.setColor(relleno);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

            if (colorBorde != null) {
                g2.setColor(isEnabled() ? colorBorde : new Color(0xD8, 0xDA, 0xE0));
                g2.setStroke(new java.awt.BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            }
            g2.dispose();

            super.paintComponent(g);
        }
    }

    // ------------------------------------------------------------------
    // Etiquetas
    // ------------------------------------------------------------------

    public static JLabel crearTitulo(String texto) {
        JLabel etiqueta = new JLabel(texto);
        etiqueta.setFont(FUENTE_TITULO);
        etiqueta.setForeground(TEXTO);
        return etiqueta;
    }

    public static JLabel crearEtiquetaSecundaria(String texto) {
        JLabel etiqueta = new JLabel(texto);
        etiqueta.setFont(FUENTE_ETIQUETA);
        etiqueta.setForeground(TEXTO_SECUNDARIO);
        return etiqueta;
    }

    // ------------------------------------------------------------------
    // Tablas
    // ------------------------------------------------------------------

    /** Aplica el estilo plano (sin líneas de grilla, filas alternadas, selección de color) a una tabla. */
    public static void aplicarEstiloTabla(JTable tabla) {
        tabla.setRowHeight(26);
        tabla.setShowGrid(false);
        tabla.setIntercellSpacing(new Dimension(0, 0));
        tabla.setFont(FUENTE_MONO);
        tabla.setForeground(TEXTO);
        tabla.setSelectionBackground(PRIMARIO);
        tabla.setSelectionForeground(Color.WHITE);
        tabla.setFillsViewportHeight(true);

        tabla.getTableHeader().setFont(FUENTE_ETIQUETA);
        tabla.getTableHeader().setForeground(TEXTO_SECUNDARIO);
        tabla.getTableHeader().setBackground(PANEL);
        tabla.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDE));
        tabla.getTableHeader().setPreferredSize(new Dimension(0, 30));

        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                             boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? PANEL : FONDO);
                }
                setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
                return c;
            }
        };
        renderer.setHorizontalAlignment(SwingConstants.LEFT);

        for (int i = 0; i < tabla.getColumnCount(); i++) {
            tabla.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
    }

    // ------------------------------------------------------------------
    // iconos simples (dibujados a mano, sin depender de fuentes de emoji)
    // ------------------------------------------------------------------

    public static javax.swing.Icon iconoMemoria(Color color) {
        return new IconoSimple(IconoSimple.Tipo.MEMORIA, color, 15);
    }

    public static javax.swing.Icon iconoSistema(Color color) {
        return new IconoSimple(IconoSimple.Tipo.SISTEMA, color, 15);
    }

    public static javax.swing.Icon iconoUsuario(Color color) {
        return new IconoSimple(IconoSimple.Tipo.USUARIO, color, 15);
    }

    /** Pequeños iconos vectoriales (chip de memoria, escudo de sistema, persona). */
    private static class IconoSimple implements javax.swing.Icon {
        enum Tipo { MEMORIA, SISTEMA, USUARIO }

        private final Tipo tipo;
        private final Color color;
        private final int tam;

        IconoSimple(Tipo tipo, Color color, int tam) {
            this.tipo = tipo;
            this.color = color;
            this.tam = tam;
        }

        @Override
        public int getIconWidth() {
            return tam;
        }

        @Override
        public int getIconHeight() {
            return tam;
        }

        @Override
        public void paintIcon(Component c, java.awt.Graphics g, int x, int y) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.translate(x, y);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new java.awt.BasicStroke(1.3f));

            switch (tipo) {
                case MEMORIA:
                    dibujarMemoria(g2);
                    break;
                case SISTEMA:
                    dibujarSistema(g2);
                    break;
                case USUARIO:
                    dibujarUsuario(g2);
                    break;
            }
            g2.dispose();
        }

        /** Chip de memoria: cuerpo cuadrado con "patas" arriba y abajo. */
        private void dibujarMemoria(java.awt.Graphics2D g2) {
            g2.drawRoundRect(3, 3, tam - 6, tam - 6, 2, 2);
            for (int i = 0; i < 3; i++) {
                int pos = 5 + i * 3;
                g2.drawLine(pos, 0, pos, 3);
                g2.drawLine(pos, tam - 3, pos, tam);
            }
        }

        /** Escudo simple, representando el espacio del Sistema Operativo/Kernel. */
        private void dibujarSistema(java.awt.Graphics2D g2) {
            int pad = 2;
            java.awt.Polygon escudo = new java.awt.Polygon();
            escudo.addPoint(tam / 2, pad);
            escudo.addPoint(tam - pad, pad + 3);
            escudo.addPoint(tam - pad, tam / 2 + 1);
            escudo.addPoint(tam / 2, tam - pad);
            escudo.addPoint(pad, tam / 2 + 1);
            escudo.addPoint(pad, pad + 3);
            g2.drawPolygon(escudo);
        }

        /** Silueta simple de una persona, representando el espacio de Usuario. */
        private void dibujarUsuario(java.awt.Graphics2D g2) {
            int diametroCabeza = tam / 2 - 1;
            g2.drawOval((tam - diametroCabeza) / 2, 1, diametroCabeza, diametroCabeza);
            g2.drawArc(1, tam / 2, tam - 2, tam / 2, 0, 180);
        }
    }
}
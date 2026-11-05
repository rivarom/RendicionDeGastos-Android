package com.invap.rendiciondegastos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Escritor mínimo de PDF sin dependencias: páginas con texto (Helvetica), rectángulos e imágenes JPEG
 * incrustadas tal cual (DCTDecode), por lo que el archivo pesa casi lo mismo que los JPEG.
 * Las coordenadas tienen el origen arriba a la izquierda y el eje Y hacia abajo, en puntos (1/72 pulgada).
 */
public final class PdfSimple {

    public static final int HELVETICA = 0;
    public static final int HELVETICA_NEGRITA = 1;

    private static final Charset WIN1252 = Charset.forName("windows-1252");

    private static final int[] ANCHO_H = {761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,278,278,355,556,556,889,667,191,333,333,389,584,278,333,278,278,556,556,556,556,556,556,556,556,556,556,278,278,584,584,584,556,1015,667,667,722,722,667,611,778,722,278,500,667,556,833,722,778,667,778,722,667,611,722,667,944,667,667,611,278,278,278,469,556,333,556,556,500,556,556,278,556,556,222,222,500,222,833,556,556,556,556,333,500,278,556,500,722,500,500,500,334,260,334,584,761,556,0,222,556,333,1000,556,556,333,1000,667,333,1000,0,611,0,0,222,222,333,333,350,556,1000,333,1000,500,333,944,0,500,667,278,333,556,556,556,556,260,556,333,737,370,556,584,333,737,333,400,584,333,333,333,556,537,278,333,333,365,556,834,834,834,611,667,667,667,667,667,667,1000,722,667,667,667,667,278,278,278,278,722,722,778,778,778,778,778,584,778,722,722,722,722,667,667,611,556,556,556,556,556,556,889,500,556,556,556,556,278,278,278,278,556,556,556,556,556,556,556,584,611,556,556,556,556,500,556,500};
    private static final int[] ANCHO_B = {761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,278,333,474,556,556,889,722,238,333,333,389,584,278,333,278,278,556,556,556,556,556,556,556,556,556,556,333,333,584,584,584,611,975,722,722,722,722,667,611,778,722,278,556,722,611,833,722,778,667,778,722,667,611,722,667,944,667,667,611,333,278,333,584,556,333,556,611,556,611,556,333,611,611,278,278,556,278,889,611,611,611,611,389,556,333,611,556,778,556,556,500,389,280,389,584,761,556,0,278,556,500,1000,556,556,333,1000,667,333,1000,0,611,0,0,278,278,500,500,350,556,1000,333,1000,556,333,944,0,500,667,278,333,556,556,556,556,280,556,333,737,370,556,584,333,737,333,400,584,333,333,333,611,556,278,333,333,365,556,834,834,834,611,722,722,722,722,722,722,1000,722,667,667,667,667,278,278,278,278,722,722,778,778,778,778,778,584,778,722,722,722,722,667,667,611,556,556,556,556,556,556,889,556,556,556,556,556,278,278,278,278,611,611,611,611,611,611,611,584,611,611,611,611,611,556,611,556};

    private static final class Imagen {
        final byte[] datos; final int ancho, alto;
        Imagen(byte[] d, int a, int h) { datos = d; ancho = a; alto = h; }
    }

    private final List<Imagen> imagenes = new ArrayList<>();
    private final List<Pagina> paginas = new ArrayList<>();

    /** Una página en construcción. */
    public final class Pagina {
        final double ancho, alto;
        final StringBuilder contenido = new StringBuilder();
        final List<Integer> usadas = new ArrayList<>();

        private Pagina(double ancho, double alto) { this.ancho = ancho; this.alto = alto; }

        private double fy(double y) { return alto - y; }

        private String num(double v) { return String.format(Locale.US, "%.2f", v); }

        private String color(int rgb) {
            return String.format(Locale.US, "%.3f %.3f %.3f", ((rgb >> 16) & 255) / 255.0, ((rgb >> 8) & 255) / 255.0, (rgb & 255) / 255.0);
        }

        private String hex(String s) {
            byte[] b = s.getBytes(WIN1252);
            StringBuilder sb = new StringBuilder(b.length * 2 + 2);
            sb.append('<');
            for (byte x : b) sb.append(String.format("%02X", x & 255));
            return sb.append('>').toString();
        }

        /** Texto con la base de la línea en yBase. */
        public void texto(double x, double yBase, String s, int fuente, double tam, int rgb) {
            if (s == null || s.isEmpty()) return;
            contenido.append("BT ").append(color(rgb)).append(" rg /F").append(fuente + 1).append(' ').append(num(tam))
                    .append(" Tf ").append(num(x)).append(' ').append(num(fy(yBase))).append(" Td ").append(hex(s)).append(" Tj ET\n");
        }

        public void textoCentrado(double xCentro, double yBase, String s, int fuente, double tam, int rgb) {
            texto(xCentro - anchoTexto(s, fuente, tam) / 2, yBase, s, fuente, tam, rgb);
        }

        public void textoDerecha(double xDerecha, double yBase, String s, int fuente, double tam, int rgb) {
            texto(xDerecha - anchoTexto(s, fuente, tam), yBase, s, fuente, tam, rgb);
        }

        /** Rectángulo con borde (esquinas redondeadas si radio > 0). */
        public void rectBorde(double x, double y, double w, double h, double radio, double grosor, int rgb) {
            contenido.append(color(rgb)).append(" RG ").append(num(grosor)).append(" w ");
            double yb = fy(y + h);
            if (radio <= 0) {
                contenido.append(num(x)).append(' ').append(num(yb)).append(' ').append(num(w)).append(' ').append(num(h)).append(" re S\n");
                return;
            }
            double r = Math.min(radio, Math.min(w, h) / 2), k = 0.5523 * r;
            double x2 = x + w, y2 = yb + h;
            contenido.append(num(x + r)).append(' ').append(num(yb)).append(" m ")
                    .append(num(x2 - r)).append(' ').append(num(yb)).append(" l ")
                    .append(num(x2 - r + k)).append(' ').append(num(yb)).append(' ').append(num(x2)).append(' ').append(num(yb + r - k)).append(' ').append(num(x2)).append(' ').append(num(yb + r)).append(" c ")
                    .append(num(x2)).append(' ').append(num(y2 - r)).append(" l ")
                    .append(num(x2)).append(' ').append(num(y2 - r + k)).append(' ').append(num(x2 - r + k)).append(' ').append(num(y2)).append(' ').append(num(x2 - r)).append(' ').append(num(y2)).append(" c ")
                    .append(num(x + r)).append(' ').append(num(y2)).append(" l ")
                    .append(num(x + r - k)).append(' ').append(num(y2)).append(' ').append(num(x)).append(' ').append(num(y2 - r + k)).append(' ').append(num(x)).append(' ').append(num(y2 - r)).append(" c ")
                    .append(num(x)).append(' ').append(num(yb + r)).append(" l ")
                    .append(num(x)).append(' ').append(num(yb + r - k)).append(' ').append(num(x + r - k)).append(' ').append(num(yb)).append(' ').append(num(x + r)).append(' ').append(num(yb)).append(" c h S\n");
        }

        /**
         * Dibuja una imagen en la caja (x, y) de w x h (ya como se ve en la página).
         * Con girar90 la foto se gira 90° en sentido horario: su borde superior queda a la derecha.
         */
        public void imagen(int indice, double x, double y, double w, double h, boolean girar90) {
            if (!usadas.contains(indice)) usadas.add(indice);
            double yb = fy(y + h);
            contenido.append("q ");
            if (!girar90) {
                contenido.append(num(w)).append(" 0 0 ").append(num(h)).append(' ').append(num(x)).append(' ').append(num(yb));
            } else {
                contenido.append("0 ").append(num(-h)).append(' ').append(num(w)).append(" 0 ").append(num(x)).append(' ').append(num(yb + h));
            }
            contenido.append(" cm /Im").append(indice).append(" Do Q\n");
        }
    }

    public Pagina nuevaPagina(double ancho, double alto) {
        Pagina p = new Pagina(ancho, alto);
        paginas.add(p);
        return p;
    }

    /** Registra un JPEG (bytes completos del archivo) y devuelve su índice para {@link Pagina#imagen}. */
    public int agregarJpeg(byte[] jpeg, int anchoPx, int altoPx) {
        imagenes.add(new Imagen(jpeg, anchoPx, altoPx));
        return imagenes.size() - 1;
    }

    // ------------------------------------------------------------------ medidas de texto

    public static double anchoTexto(String s, int fuente, double tam) {
        int[] t = fuente == HELVETICA_NEGRITA ? ANCHO_B : ANCHO_H;
        byte[] b = s.getBytes(WIN1252);
        double total = 0;
        for (byte x : b) total += t[x & 255];
        return total * tam / 1000.0;
    }

    /** Parte [texto] en líneas de hasta [maxAncho] puntos; respeta los saltos de línea y corta palabras muy largas. */
    public static List<String> envolver(String texto, int fuente, double tam, double maxAncho) {
        List<String> lineas = new ArrayList<>();
        for (String parrafo : texto.split("\\r?\\n", -1)) {
            StringBuilder actual = new StringBuilder();
            for (String palabra : parrafo.split(" ")) {
                String candidata = actual.length() == 0 ? palabra : actual + " " + palabra;
                if (anchoTexto(candidata, fuente, tam) <= maxAncho) {
                    actual.setLength(0);
                    actual.append(candidata);
                    continue;
                }
                if (actual.length() > 0) {
                    lineas.add(actual.toString());
                    actual.setLength(0);
                }
                String resto = palabra;
                while (anchoTexto(resto, fuente, tam) > maxAncho && resto.length() > 1) {
                    int n = resto.length() - 1;
                    while (n > 1 && anchoTexto(resto.substring(0, n), fuente, tam) > maxAncho) n--;
                    lineas.add(resto.substring(0, n));
                    resto = resto.substring(n);
                }
                actual.append(resto);
            }
            lineas.add(actual.toString());
        }
        return lineas;
    }

    // ------------------------------------------------------------------ escritura

    private static final class Salida {
        final OutputStream out;
        long pos = 0;
        final List<Long> offsets = new ArrayList<>();
        Salida(OutputStream o) { out = o; }
        void bytes(byte[] b) throws IOException { out.write(b); pos += b.length; }
        void texto(String s) throws IOException { bytes(s.getBytes(Charset.forName("ISO-8859-1"))); }
        void inicioObjeto(int n) throws IOException {
            while (offsets.size() < n) offsets.add(0L);
            offsets.set(n - 1, pos);
            texto(n + " 0 obj\n");
        }
        void finObjeto() throws IOException { texto("endobj\n"); }
    }

    public void guardar(OutputStream destino) throws IOException {
        if (paginas.isEmpty()) throw new IllegalStateException("El PDF no tiene páginas");
        Salida s = new Salida(destino);
        // Numeración: 1 catálogo, 2 páginas, 3 y 4 fuentes, imágenes, luego (página, contenido) por cada página
        int primeraImagen = 5;
        int primeraPagina = primeraImagen + imagenes.size();
        s.texto("%PDF-1.4\n");
        s.bytes(new byte[]{'%', (byte) 0xE2, (byte) 0xE3, (byte) 0xCF, (byte) 0xD3, '\n'});

        s.inicioObjeto(1);
        s.texto("<< /Type /Catalog /Pages 2 0 R >>\n");
        s.finObjeto();

        s.inicioObjeto(2);
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < paginas.size(); i++) kids.append(primeraPagina + 2 * i).append(" 0 R ");
        s.texto("<< /Type /Pages /Count " + paginas.size() + " /Kids [" + kids + "] >>\n");
        s.finObjeto();

        s.inicioObjeto(3);
        s.texto("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\n");
        s.finObjeto();
        s.inicioObjeto(4);
        s.texto("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>\n");
        s.finObjeto();

        for (int i = 0; i < imagenes.size(); i++) {
            Imagen im = imagenes.get(i);
            s.inicioObjeto(primeraImagen + i);
            s.texto("<< /Type /XObject /Subtype /Image /Width " + im.ancho + " /Height " + im.alto
                    + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length " + im.datos.length + " >>\nstream\n");
            s.bytes(im.datos);
            s.texto("\nendstream\n");
            s.finObjeto();
        }

        for (int i = 0; i < paginas.size(); i++) {
            Pagina p = paginas.get(i);
            int nPagina = primeraPagina + 2 * i;
            int nContenido = nPagina + 1;
            StringBuilder xobj = new StringBuilder();
            for (int idx : p.usadas) xobj.append("/Im").append(idx).append(' ').append(primeraImagen + idx).append(" 0 R ");
            s.inicioObjeto(nPagina);
            s.texto("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + p.num(p.ancho) + " " + p.num(p.alto) + "] "
                    + "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> /XObject << " + xobj + ">> >> /Contents " + nContenido + " 0 R >>\n");
            s.finObjeto();

            ByteArrayOutputStream comprimido = new ByteArrayOutputStream();
            try (DeflaterOutputStream d = new DeflaterOutputStream(comprimido, new Deflater(Deflater.BEST_COMPRESSION))) {
                d.write(p.contenido.toString().getBytes(Charset.forName("ISO-8859-1")));
            }
            byte[] datos = comprimido.toByteArray();
            s.inicioObjeto(nContenido);
            s.texto("<< /Filter /FlateDecode /Length " + datos.length + " >>\nstream\n");
            s.bytes(datos);
            s.texto("\nendstream\n");
            s.finObjeto();
        }

        int total = primeraPagina + 2 * paginas.size();   // cantidad de objetos + 1 (objeto 0)
        long inicioXref = s.pos;
        s.texto("xref\n0 " + total + "\n");
        s.texto("0000000000 65535 f \n");
        for (int i = 0; i < total - 1; i++) s.texto(String.format(Locale.US, "%010d 00000 n \n", s.offsets.get(i)));
        s.texto("trailer\n<< /Size " + total + " /Root 1 0 R >>\nstartxref\n" + inicioXref + "\n%%EOF\n");
        s.out.flush();
    }
}

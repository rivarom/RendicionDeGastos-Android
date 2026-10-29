package com.invap.rendiciondegastos;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Escritor mínimo de archivos .xlsx (Office Open XML) sin dependencias.
 * Soporta lo que usa la rendición: estilos, bordes, rellenos, formatos numéricos, celdas combinadas,
 * anchos/altos, columnas ocultas, fórmulas, formato condicional e impresión apaisada ajustada al ancho.
 * Los índices de fila y columna son de base 0.
 */
public final class XlsxLibro {

    /** Descripción de un estilo de celda. Se usa con {@link XlsxLibro#estilo(Estilo)}. */
    public static final class Estilo {
        int tam = 10;
        boolean negrita, cursiva, ajustar;
        String colorTexto, relleno, formatoNumero;
        int borde;      // 0 ninguno, 1 fino, 2 medio
        int alinH;      // 0 general, 1 centro
        int alinV;      // 0 abajo, 1 centro, 2 arriba

        public Estilo tam(int v) { tam = v; return this; }
        public Estilo negrita() { negrita = true; return this; }
        public Estilo cursiva() { cursiva = true; return this; }
        public Estilo ajustar() { ajustar = true; return this; }
        public Estilo colorTexto(String rrggbb) { colorTexto = rrggbb; return this; }
        public Estilo relleno(String rrggbb) { relleno = rrggbb; return this; }
        public Estilo formatoNumero(String f) { formatoNumero = f; return this; }
        public Estilo borde(int b) { borde = b; return this; }
        public Estilo centrado() { alinH = 1; return this; }
        public Estilo vCentro() { alinV = 1; return this; }
        public Estilo vArriba() { alinV = 2; return this; }

        String clave() {
            return tam + "|" + negrita + "|" + cursiva + "|" + ajustar + "|" + colorTexto + "|" + relleno + "|"
                    + formatoNumero + "|" + borde + "|" + alinH + "|" + alinV;
        }
    }

    private static final class Celda {
        final int tipo; // 0 texto, 1 número, 2 fórmula, 3 vacía
        final String texto;
        final double numero;
        final int estilo;
        Celda(int tipo, String texto, double numero, int estilo) {
            this.tipo = tipo; this.texto = texto; this.numero = numero; this.estilo = estilo;
        }
    }

    public final class Hoja {
        private final String nombre;
        private final TreeMap<Integer, TreeMap<Integer, Celda>> filas = new TreeMap<>();
        private final TreeMap<Integer, Double> altos = new TreeMap<>();
        private final TreeMap<Integer, double[]> columnas = new TreeMap<>(); // col -> {ancho, oculta}
        private final List<String> combinadas = new ArrayList<>();
        private final List<String[]> condicionales = new ArrayList<>();     // {rango, formula, dxfId}

        private int[] logo; // {fila, col, offsetXEmu, anchoEmu, altoEmu}

        private Hoja(String nombre) { this.nombre = nombre; }

        private static final int EMU_POR_PX = 9525;

        private int anchoColumnaPx(int col) {
            double[] c = columnas.get(col);
            double ancho = c != null ? c[0] : 8.43;
            return (int) Math.round(ancho * 7 + 5);
        }

        /**
         * Coloca el logo (PNG del libro) centrado entre las columnas colDesde y colHasta, con su borde superior
         * en la fila indicada. Las columnas deben tener su ancho definido antes de llamar.
         */
        public void logoCentrado(int fila, int colDesde, int colHasta, int anchoPx, int altoPx) {
            int total = 0;
            for (int c = colDesde; c <= colHasta; c++) total += anchoColumnaPx(c);
            int x = Math.max(0, (total - anchoPx) / 2);
            int col = colDesde;
            while (col < colHasta && x >= anchoColumnaPx(col)) {
                x -= anchoColumnaPx(col);
                col++;
            }
            logo = new int[]{fila, col, x * EMU_POR_PX, anchoPx * EMU_POR_PX, altoPx * EMU_POR_PX};
        }

        private void poner(int fila, int col, Celda c) {
            filas.computeIfAbsent(fila, k -> new TreeMap<>()).put(col, c);
        }

        public void texto(int fila, int col, String valor, int estilo) { poner(fila, col, new Celda(0, valor == null ? "" : valor, 0, estilo)); }
        public void numero(int fila, int col, double valor, int estilo) { poner(fila, col, new Celda(1, null, valor, estilo)); }
        public void formula(int fila, int col, String formula, int estilo) { poner(fila, col, new Celda(2, formula, 0, estilo)); }
        public void vacio(int fila, int col, int estilo) { poner(fila, col, new Celda(3, null, 0, estilo)); }

        /** Combina el rango; el contenido y el estilo se ponen en la celda superior izquierda. */
        public void combinar(int fila1, int col1, int fila2, int col2) {
            combinadas.add(ref(col1, fila1) + ":" + ref(col2, fila2));
        }

        /** Ancho en caracteres. */
        public void anchoColumna(int col, double ancho) { columnas.put(col, new double[]{ancho, 0}); }
        public void ocultarColumna(int col, double ancho) { columnas.put(col, new double[]{ancho, 1}); }
        /** Alto en puntos. */
        public void altoFila(int fila, double puntos) { altos.put(fila, puntos); }

        /** Regla de formato condicional por fórmula (relativa a la primera celda del rango). */
        public void formatoCondicional(String rango, String formula, int dxf) {
            condicionales.add(new String[]{rango, formula, String.valueOf(dxf)});
        }
    }

    private byte[] logoPng;

    /** Imagen PNG que usan las hojas que llaman a {@link Hoja#logoCentrado}. */
    public void logoPng(byte[] png) { this.logoPng = png; }

    private boolean hojaConLogo(Hoja h) { return logoPng != null && h.logo != null; }

    private final List<Hoja> hojas = new ArrayList<>();
    private final LinkedHashMap<String, Integer> estilos = new LinkedHashMap<>();
    private final List<Estilo> listaEstilos = new ArrayList<>();
    private final List<String> dxfs = new ArrayList<>();

    public XlsxLibro() {
        estilo(new Estilo()); // estilo 0 = predeterminado
    }

    public int estilo(Estilo e) {
        String k = e.clave();
        Integer id = estilos.get(k);
        if (id != null) return id;
        int nuevo = listaEstilos.size();
        estilos.put(k, nuevo);
        listaEstilos.add(e);
        return nuevo;
    }

    /** Estilo diferencial (para formato condicional) con relleno de color; devuelve su índice. */
    public int dxfRelleno(String rrggbb) {
        dxfs.add("<dxf><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF" + rrggbb + "\"/><bgColor rgb=\"FF" + rrggbb + "\"/></patternFill></fill></dxf>");
        return dxfs.size() - 1;
    }

    public Hoja hoja(String nombre) {
        Hoja h = new Hoja(nombre);
        hojas.add(h);
        return h;
    }

    public int cantidadHojas() { return hojas.size(); }

    public boolean existeHoja(String nombre) {
        for (Hoja h : hojas) if (h.nombre.equalsIgnoreCase(nombre)) return true;
        return false;
    }

    // ------------------------------------------------------------------ escritura

    public static String letra(int col) {
        StringBuilder sb = new StringBuilder();
        int n = col;
        do {
            sb.insert(0, (char) ('A' + (n % 26)));
            n = n / 26 - 1;
        } while (n >= 0);
        return sb.toString();
    }

    private static String ref(int col, int fila) { return letra(col) + (fila + 1); }

    private static String esc(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                default:
                    // caracteres de control no válidos en XML 1.0
                    if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String num(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "0";
        return new java.math.BigDecimal(Double.toString(v)).toPlainString();
    }

    public void guardar(OutputStream salida) throws IOException {
        if (hojas.isEmpty()) throw new IllegalStateException("El libro no tiene hojas");
        ZipOutputStream zip = new ZipOutputStream(salida);
        entrada(zip, "[Content_Types].xml", contentTypes());
        entrada(zip, "_rels/.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>");
        entrada(zip, "xl/workbook.xml", workbook());
        entrada(zip, "xl/_rels/workbook.xml.rels", workbookRels());
        entrada(zip, "xl/styles.xml", styles());
        boolean hayLogo = false;
        for (int i = 0; i < hojas.size(); i++) {
            Hoja h = hojas.get(i);
            entrada(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", hoja(h));
            if (hojaConLogo(h)) {
                hayLogo = true;
                entrada(zip, "xl/worksheets/_rels/sheet" + (i + 1) + ".xml.rels",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing\" Target=\"../drawings/drawing" + (i + 1) + ".xml\"/>"
                        + "</Relationships>");
                entrada(zip, "xl/drawings/drawing" + (i + 1) + ".xml", dibujo(h));
                entrada(zip, "xl/drawings/_rels/drawing" + (i + 1) + ".xml.rels",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"../media/logo.png\"/>"
                        + "</Relationships>");
            }
        }
        if (hayLogo) {
            zip.putNextEntry(new ZipEntry("xl/media/logo.png"));
            zip.write(logoPng);
            zip.closeEntry();
        }
        zip.finish();
        zip.flush();
    }

    private static void entrada(ZipOutputStream zip, String nombre, String contenido) throws IOException {
        zip.putNextEntry(new ZipEntry(nombre));
        zip.write(contenido.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String contentTypes() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Default Extension=\"png\" ContentType=\"image/png\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int i = 0; i < hojas.size(); i++) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i + 1)
              .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
            if (hojaConLogo(hojas.get(i))) {
                sb.append("<Override PartName=\"/xl/drawings/drawing").append(i + 1)
                  .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>");
            }
        }
        return sb.append("</Types>").toString();
    }

    private String workbook() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
        for (int i = 0; i < hojas.size(); i++) {
            sb.append("<sheet name=\"").append(esc(hojas.get(i).nombre)).append("\" sheetId=\"").append(i + 1)
              .append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        return sb.append("</sheets><calcPr calcId=\"191029\" fullCalcOnLoad=\"1\"/></workbook>").toString();
    }

    private String workbookRels() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 0; i < hojas.size(); i++) {
            sb.append("<Relationship Id=\"rId").append(i + 1)
              .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
              .append(i + 1).append(".xml\"/>");
        }
        sb.append("<Relationship Id=\"rId").append(hojas.size() + 1)
          .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        return sb.append("</Relationships>").toString();
    }

    private String styles() {
        // formatos numéricos personalizados
        LinkedHashMap<String, Integer> numFmts = new LinkedHashMap<>();
        for (Estilo e : listaEstilos) {
            if (e.formatoNumero != null && !numFmts.containsKey(e.formatoNumero)) numFmts.put(e.formatoNumero, 164 + numFmts.size());
        }
        // fuentes, rellenos y bordes únicos
        LinkedHashMap<String, Integer> fuentes = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> rellenos = new LinkedHashMap<>();
        rellenos.put("none", 0);
        rellenos.put("gray125", 1);
        LinkedHashMap<Integer, Integer> bordes = new LinkedHashMap<>();
        bordes.put(0, 0);
        for (Estilo e : listaEstilos) {
            fuentes.putIfAbsent(fuenteXml(e), fuentes.size());
            if (e.relleno != null) rellenos.putIfAbsent(e.relleno, rellenos.size());
            bordes.putIfAbsent(e.borde, bordes.size());
        }
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        if (!numFmts.isEmpty()) {
            sb.append("<numFmts count=\"").append(numFmts.size()).append("\">");
            for (Map.Entry<String, Integer> en : numFmts.entrySet()) {
                sb.append("<numFmt numFmtId=\"").append(en.getValue()).append("\" formatCode=\"").append(esc(en.getKey())).append("\"/>");
            }
            sb.append("</numFmts>");
        }
        sb.append("<fonts count=\"").append(fuentes.size()).append("\">");
        for (String f : fuentes.keySet()) sb.append(f);
        sb.append("</fonts><fills count=\"").append(rellenos.size()).append("\">");
        for (String r : rellenos.keySet()) {
            if (r.equals("none")) sb.append("<fill><patternFill patternType=\"none\"/></fill>");
            else if (r.equals("gray125")) sb.append("<fill><patternFill patternType=\"gray125\"/></fill>");
            else sb.append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF").append(r).append("\"/><bgColor indexed=\"64\"/></patternFill></fill>");
        }
        sb.append("</fills><borders count=\"").append(bordes.size()).append("\">");
        for (Integer b : bordes.keySet()) {
            if (b == 0) { sb.append("<border><left/><right/><top/><bottom/><diagonal/></border>"); continue; }
            String est = b == 2 ? "medium" : "thin";
            sb.append("<border>");
            for (String lado : new String[]{"left", "right", "top", "bottom"}) {
                sb.append("<").append(lado).append(" style=\"").append(est).append("\"><color auto=\"1\"/></").append(lado).append(">");
            }
            sb.append("<diagonal/></border>");
        }
        sb.append("</borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>");
        sb.append("<cellXfs count=\"").append(listaEstilos.size()).append("\">");
        for (Estilo e : listaEstilos) {
            int numId = e.formatoNumero == null ? 0 : numFmts.get(e.formatoNumero);
            int fuenteId = fuentes.get(fuenteXml(e));
            int rellenoId = e.relleno == null ? 0 : rellenos.get(e.relleno);
            int bordeId = bordes.get(e.borde);
            sb.append("<xf numFmtId=\"").append(numId).append("\" fontId=\"").append(fuenteId)
              .append("\" fillId=\"").append(rellenoId).append("\" borderId=\"").append(bordeId).append("\" xfId=\"0\"");
            if (numId != 0) sb.append(" applyNumberFormat=\"1\"");
            sb.append(" applyFont=\"1\"");
            if (rellenoId != 0) sb.append(" applyFill=\"1\"");
            if (bordeId != 0) sb.append(" applyBorder=\"1\"");
            boolean alin = e.alinH != 0 || e.alinV != 0 || e.ajustar;
            if (alin) {
                sb.append(" applyAlignment=\"1\"><alignment");
                if (e.alinH == 1) sb.append(" horizontal=\"center\"");
                if (e.alinV == 1) sb.append(" vertical=\"center\"");
                else if (e.alinV == 2) sb.append(" vertical=\"top\"");
                if (e.ajustar) sb.append(" wrapText=\"1\"");
                sb.append("/></xf>");
            } else {
                sb.append("/>");
            }
        }
        sb.append("</cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>");
        sb.append("<dxfs count=\"").append(dxfs.size()).append("\">");
        for (String d : dxfs) sb.append(d);
        sb.append("</dxfs></styleSheet>");
        return sb.toString();
    }

    private static String fuenteXml(Estilo e) {
        StringBuilder sb = new StringBuilder("<font>");
        if (e.negrita) sb.append("<b/>");
        if (e.cursiva) sb.append("<i/>");
        sb.append("<sz val=\"").append(e.tam).append("\"/>");
        if (e.colorTexto != null) sb.append("<color rgb=\"FF").append(e.colorTexto).append("\"/>");
        sb.append("<name val=\"Arial\"/><family val=\"2\"/></font>");
        return sb.toString();
    }

    private String hoja(Hoja h) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheetPr><pageSetUpPr fitToPage=\"1\"/></sheetPr>"
                + "<sheetViews><sheetView workbookViewId=\"0\" showGridLines=\"0\"/></sheetViews>"
                + "<sheetFormatPr defaultRowHeight=\"13.5\"/>");
        if (!h.columnas.isEmpty()) {
            sb.append("<cols>");
            for (Map.Entry<Integer, double[]> en : h.columnas.entrySet()) {
                int c = en.getKey() + 1;
                sb.append("<col min=\"").append(c).append("\" max=\"").append(c).append("\" width=\"")
                  .append(String.format(Locale.US, "%.2f", en.getValue()[0])).append("\" customWidth=\"1\"");
                if (en.getValue()[1] == 1) sb.append(" hidden=\"1\"");
                sb.append("/>");
            }
            sb.append("</cols>");
        }
        sb.append("<sheetData>");
        TreeMap<Integer, Boolean> todas = new TreeMap<>();
        for (Integer f : h.filas.keySet()) todas.put(f, true);
        for (Integer f : h.altos.keySet()) todas.put(f, true);
        for (Integer f : todas.keySet()) {
            sb.append("<row r=\"").append(f + 1).append("\"");
            Double alto = h.altos.get(f);
            if (alto != null) sb.append(" ht=\"").append(String.format(Locale.US, "%.2f", alto)).append("\" customHeight=\"1\"");
            sb.append(">");
            TreeMap<Integer, Celda> fila = h.filas.get(f);
            if (fila != null) {
                for (Map.Entry<Integer, Celda> en : fila.entrySet()) {
                    Celda c = en.getValue();
                    String r = ref(en.getKey(), f);
                    switch (c.tipo) {
                        case 0:
                            sb.append("<c r=\"").append(r).append("\" s=\"").append(c.estilo).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                              .append(esc(c.texto)).append("</t></is></c>");
                            break;
                        case 1:
                            sb.append("<c r=\"").append(r).append("\" s=\"").append(c.estilo).append("\"><v>").append(num(c.numero)).append("</v></c>");
                            break;
                        case 2:
                            sb.append("<c r=\"").append(r).append("\" s=\"").append(c.estilo).append("\"><f>").append(esc(c.texto)).append("</f></c>");
                            break;
                        default:
                            sb.append("<c r=\"").append(r).append("\" s=\"").append(c.estilo).append("\"/>");
                    }
                }
            }
            sb.append("</row>");
        }
        sb.append("</sheetData>");
        if (!h.combinadas.isEmpty()) {
            sb.append("<mergeCells count=\"").append(h.combinadas.size()).append("\">");
            for (String m : h.combinadas) sb.append("<mergeCell ref=\"").append(m).append("\"/>");
            sb.append("</mergeCells>");
        }
        int prioridad = 1;
        for (String[] cf : h.condicionales) {
            sb.append("<conditionalFormatting sqref=\"").append(cf[0]).append("\"><cfRule type=\"expression\" dxfId=\"")
              .append(cf[2]).append("\" priority=\"").append(prioridad++).append("\"><formula>").append(esc(cf[1])).append("</formula></cfRule></conditionalFormatting>");
        }
        sb.append("<pageMargins left=\"0.4\" right=\"0.4\" top=\"0.5\" bottom=\"0.5\" header=\"0.3\" footer=\"0.3\"/>");
        sb.append("<pageSetup paperSize=\"9\" orientation=\"landscape\" fitToWidth=\"1\" fitToHeight=\"0\"/>");
        if (hojaConLogo(h)) sb.append("<drawing r:id=\"rId1\"/>");
        sb.append("</worksheet>");
        return sb.toString();
    }

    private String dibujo(Hoja h) {
        int[] l = h.logo;
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<xdr:wsDr xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\" "
            + "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" "
            + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
            + "<xdr:oneCellAnchor><xdr:from><xdr:col>" + l[1] + "</xdr:col><xdr:colOff>" + l[2] + "</xdr:colOff>"
            + "<xdr:row>" + l[0] + "</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from>"
            + "<xdr:ext cx=\"" + l[3] + "\" cy=\"" + l[4] + "\"/>"
            + "<xdr:pic><xdr:nvPicPr><xdr:cNvPr id=\"2\" name=\"Logo INVAP\"/><xdr:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></xdr:cNvPicPr></xdr:nvPicPr>"
            + "<xdr:blipFill><a:blip r:embed=\"rId1\"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>"
            + "<xdr:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"" + l[3] + "\" cy=\"" + l[4] + "\"/></a:xfrm>"
            + "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic><xdr:clientData/></xdr:oneCellAnchor></xdr:wsDr>";
    }
}

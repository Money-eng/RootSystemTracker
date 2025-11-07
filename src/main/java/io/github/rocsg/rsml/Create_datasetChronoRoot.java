package io.github.rocsg.rsml;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Script/outil CLI :
 *  - arg0 = chemin du dossier racine contenant des sous-dossiers d’images
 *  - arg1 = dossier de sortie pour les .tif générés
 *
 * Exemple :
 *   java -cp target/monjar-avec-deps.jar io.github.rocsg.rsml.Create_datasetChronoRoot "D:/images" "D:/out"
 */
public class Create_datasetChronoRoot {

    private static final Set<String> IMG_EXT = new HashSet<>(Arrays.asList(
            "tif","tiff","png","jpg","jpeg","bmp","gif"));

    private static final boolean isMask = true;
    // Titre demandé pour chaque slice
    private static final DateTimeFormatter TITLE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    // Parsers pour les formats de dates qu'exiftool renvoie fréquemment
    private static final DateTimeFormatter[] CANDIDATE_PARSERS = new DateTimeFormatter[]{
            // 2025:09:30 11:16:49+02:00
            DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ssXXX"),
            // 2025:09:30 11:16:49 (sans fuseau)
            DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"),
            // 2019-12-05T16:19:45+01:00
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            // 2019-12-05T16:19:45Z ou avec zone
            DateTimeFormatter.ISO_ZONED_DATE_TIME,
            // 2019-12-05T16:19:45 (sans fuseau)
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    };

    // Choisis un fuseau "stable" plutôt que le système courant.
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Paris");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        ij.ImageJ ij = new ImageJ();
        // --- Exemple avec chemins codés en dur (remplace par args si tu veux en CLI) ---
        File root   = new File("/home/loai/Images/DataTest/Chronoroot/Compiled/Pas_dans_le_dataset/labels_filtered");//"/home/loai/Images/DataTest/Chronoroot/Compiled/TrainSet/labels_filtered/");
        File outDir = new File("/home/loai/Images/DataTest/Chronoroot/Compiled/TrainSet/labels");

        if (!root.isDirectory()) {
            System.err.println("Le dossier d'entrée n'existe pas: " + root.getAbsolutePath());
            System.exit(2);
        }
        if (!outDir.exists() && !outDir.mkdirs()) {
            System.err.println("Impossible de créer le dossier de sortie: " + outDir.getAbsolutePath());
            System.exit(3);
        }

        if (!isExifToolAvailable()) {
            System.err.println("ExifTool introuvable dans le PATH. Installe-le (ex: apt install libimage-exiftool-perl).");
            System.exit(4);
        }

        File[] subDirs = Optional.ofNullable(root.listFiles(File::isDirectory))
                .orElse(new File[0]);

        if (subDirs.length == 0) {
            System.err.println("Aucun sous-dossier trouvé dans: " + root.getAbsolutePath());
            System.exit(0);
        }

        for (File folder : subDirs) {
            try {
                processFolder(folder, outDir);
            } catch (Exception e) {
                System.err.println("Erreur sur le dossier " + folder.getName() + " : " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("Terminé.");
    }

    private static void processFolder(File folder, File outDir) throws IOException {
        // 1) Lister et trier par ordre numérique naturel du nom (image_75_0000.png < image_100_0000.png)
        List<File> images = Files.list(folder.toPath())
                .map(java.nio.file.Path::toFile)
                .filter(f -> f.isFile() && hasImgExt(f.getName()))
                .sorted(Comparator
                        .comparingInt((File f) -> firstIntInName(f.getName()))
                        .thenComparing(File::getName))
                .collect(Collectors.toList());

        if (images.isEmpty()) {
            System.out.println("Aucune image dans " + folder.getName() + " — ignoré.");
            return;
        }

        ImageStack stack = null;
        ImagePlus template = null;
        ImagePlus out = null;
        File outFile = new File(outDir, folder.getName() + ".tif");
        // 2) Lire les dates via ExifTool (une par une pour éviter les problèmes de longueur de commande)
        if (!isMask) {
            List<ImageEntry> entries = new ArrayList<>(images.size());
            for (File f : images) {
                Instant when = readInstantFromExifToolOrNull(f); // *** NOUVEAU ***
                int numKey = firstIntInName(f.getName());
                entries.add(new ImageEntry(f, when, numKey));
            }

            // 3) Si TOUTES les images ont une date -> tri par date, sinon on conserve l'ordre numérico-lexical
            boolean allHaveDates = entries.stream().allMatch(e -> e.instant != null);
            if (allHaveDates) {
                entries.sort(Comparator.comparing(e -> e.instant));
            }

            Instant t0 = entries.stream().filter(e -> e.instant != null)
                    .map(e -> e.instant).min(Comparator.naturalOrder())
                    .orElse(null);


            StringBuilder globalInfo = new StringBuilder();
            globalInfo.append("Source folder: ").append(folder.getAbsolutePath()).append("\n");
            if (t0 != null) globalInfo.append("First timestamp (t0): ").append(formatInstant(t0)).append("\n");
            globalInfo.append("Images:\n");

            int sliceIndex = 0;
            for (ImageEntry e : entries) {
                ImagePlus imp = IJ.openImage(e.file.getAbsolutePath());
                if (imp == null) {
                    System.err.println("Impossible d'ouvrir: " + e.file.getName() + " — ignoré.");
                    continue;
                }

                if (stack == null) {
                    stack = new ImageStack(imp.getWidth(), imp.getHeight());
                    template = imp;
                } else if (imp.getWidth() != stack.getWidth() || imp.getHeight() != stack.getHeight()) {
                    ImageProcessor ip = imp.getProcessor().resize(stack.getWidth(), stack.getHeight(), true);
                    imp.setProcessor(ip);
                }

                String title;
                if (e.instant != null && t0 != null) {
                    double hours = Duration.between(t0, e.instant).toMillis() / 3600000.0;
                    title = formatSliceTitle(e.instant, hours);
                } else {
                    title = "h0 + " + e.numKey + " (sans date EXIF)";
                }

                stack.addSlice(title, imp.getProcessor());
                sliceIndex++;

                globalInfo.append("  #").append(sliceIndex)
                        .append(" -> ").append(e.file.getName());
                if (e.instant != null) globalInfo.append(" @ ").append(formatInstant(e.instant));
                globalInfo.append(" ; title=\"").append(title).append("\"\n");

                imp.close();
            }


            if (stack == null || stack.size() == 0) {
                System.err.println("Aucune slice créée pour " + folder.getName());
                return;
            }

            out = new ImagePlus(folder.getName(), stack);
            if (template != null) {
                out.setCalibration(template.getCalibration());
                Object info = template.getProperty("Info");
                if (info instanceof String) {
                    globalInfo.append("\n--- First image original Info ---\n").append(info).append("\n");
                }
            }
            out.setProperty("Info", globalInfo.toString());
        }
        else {
            ImagePlus out_leaves_and_seed = null;
            ImageStack out_leaves = null;
            ImageStack simpleRoots = null;
            ImagePlus outo = null;
            for (File f : images) {
                ImagePlus imp = IJ.openImage(f.getAbsolutePath());
                if (imp == null) {
                    System.err.println("Impossible d'ouvrir: " + f.getName() + " — ignoré.");
                    continue;
                }

                if (out_leaves == null) {
                    out_leaves = new ImageStack(imp.getWidth(), imp.getHeight());
                    simpleRoots = new ImageStack(imp.getWidth(), imp.getHeight());
                }

                out = new ImagePlus(folder.getName(), imp.getProcessor().createProcessor(imp.getWidth(), imp.getHeight()));
                out_leaves_and_seed = new ImagePlus(folder.getName() + "leaves_seed_hypocotil_petiol", imp.getProcessor().createProcessor(imp.getWidth(), imp.getHeight()));
                // set all pixels to 0 (black) initially
                out.getProcessor().setValue(0);
                out.getProcessor().fill();
                //if (outo == null) outo = out.duplicate();
                outo = out.duplicate();

                out_leaves_and_seed.getProcessor().setValue(0);
                out_leaves_and_seed.getProcessor().fill();

                // set all pixels which value are equal to 1 or 2 to 255 (white)
                ImageProcessor ip = imp.getProcessor();
                ImageProcessor last_image = null;
                if (images.indexOf(f) > 0) last_image = simpleRoots.getProcessor(simpleRoots.getSize());
                int radius = 7;
                for (int y = 0; y < ip.getHeight(); y++) {
                    for (int x = 0; x < ip.getWidth(); x++) {
                        int v = ip.getPixel(x, y);
                        if (v == 1 || v == 2) {
                            outo.getProcessor().putPixel(x, y, 255);
                            if (radius == -1) out.getProcessor().putPixel(x, y, 255);
                            else {
                                for (int dy = -radius; dy <= radius; dy++) {
                                    for (int dx = -radius; dx <= radius; dx++) {
                                        int nx = x + dx;
                                        int ny = y + dy;
                                        if (nx >= 0 && nx < ip.getWidth() && ny >= 0 && ny < ip.getHeight()) {
                                            out.getProcessor().putPixel(nx, ny, 255);
                                        }
                                    }
                                }
                            }
                        }
                        else if (v >= 3) {
                            out_leaves_and_seed.getProcessor().putPixel(x, y, 255);
                        }
                    }
                }
                simpleRoots.addSlice(outo.getProcessor().duplicate());
                out_leaves.addSlice(folder.getName(), out_leaves_and_seed.getProcessor().duplicate());
                imp.close();
            }

            ImagePlus out_leaves_final = new ImagePlus(folder.getName(), out_leaves);
            IJ.saveAsTiff(out_leaves_final, outFile.getAbsolutePath().replace(".tif", "_leaves.tif"));

            ImagePlus simpleRoots_final = new ImagePlus(folder.getName(), simpleRoots);
            IJ.saveAsTiff(simpleRoots_final, outFile.getAbsolutePath().replace(".tif", "_Roots.tif"));

            //IJ.saveAsTiff(out_leaves_and_seed, outFile.getAbsolutePath().replace(".tif", "_leaves_seed_hypocotil_petiol.tif"));

        }

        IJ.saveAsTiff(out, outFile.getAbsolutePath());

        if (template != null) template.close();
        assert out != null;
        out.close();
    }

    // ----------------------- ExifTool helpers -----------------------

    private static boolean isExifToolAvailable() {
        try {
            Process p = new ProcessBuilder("exiftool", "-ver")
                    .redirectErrorStream(true).start();
            int code = p.waitFor();
            return code == 0;
        } catch (Exception e) {
            return false;
        }
    }



    /** Lit l'instant depuis exiftool (JSON). Retourne null si rien d'exploitable. */
    private static Instant readInstantFromExifToolOrNull(File f) {
        List<String> cmd = Arrays.asList(
                "exiftool",
                "-j", "-api", "largefilesupport=1", "-fast2",
                // on demande d'abord ce qui nous intéresse vraiment
                "-ModifyDate",
                "-DateTimeOriginal",
                "-DateCreate",
                "-DateModify",
                "-FileModifyDate",
                f.getAbsolutePath()
        );

        try {
            Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String stdout;
            try (InputStream is = proc.getInputStream(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                for (int n; (n = is.read(buf)) != -1;) baos.write(buf, 0, n);
                stdout = baos.toString("UTF-8");
            }
            if (proc.waitFor() != 0 || stdout.trim().isEmpty()) return null;


            JsonNode arr = MAPPER.readTree(stdout);
            if (!arr.isArray() || arr.isEmpty()) return null;
            JsonNode o = arr.get(0);

            // ordre de préférence : PNG tIME d'abord, puis "vraie" capture si par miracle présente,
            // puis dates d'export, puis date système de fichier en dernier recours
            String[] keys = {
                    "ModifyDate",
                    "DateTimeOriginal",
                    "DateCreate",
                    "DateModify",
                    "FileModifyDate"
            };
            for (String k : keys) {
                JsonNode v = o.get(k);
                if (v != null && !v.isNull()) {
                    Instant inst = parseExifToolDateToInstant(v.asText(), DEFAULT_ZONE);
                    if (inst != null) return inst;
                }
            }
        } catch (Exception ignore) { }
        return null;
    }

    /** Parse des formats qu'ExifTool renvoie couramment. Si pas de fuseau, on applique `zoneIfNone`. */
    private static Instant parseExifToolDateToInstant(String s, ZoneId zoneIfNone) {
        if (s == null) return null;
        s = s.trim().replaceAll("\\s+", " "); // normalise les espaces

        // 1) ISO avec offset/zone (ex. 2019-12-08T18:21:36+01:00)
        try { return OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant(); }
        catch (Exception ignored) {}
        try { return ZonedDateTime.parse(s, DateTimeFormatter.ISO_ZONED_DATE_TIME).toInstant(); }
        catch (Exception ignored) {}

        // 2) "YYYY:MM:DD HH:MM:SS+HH:MM" (ExifTool pour FileModifyDate, parfois avec offset)
        //    On tente d'abord avec offset explicite...
        try {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ssXXX");
            return OffsetDateTime.parse(s, f).toInstant();
        } catch (Exception ignored) {}

        // ...puis sans offset (ModifyDate / PNG tIME), on applique un fuseau explicite
        try {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
            LocalDateTime ldt = LocalDateTime.parse(s, f);
            return ldt.atZone(zoneIfNone).toInstant();
        } catch (Exception ignored) {}

        // 3) ISO local (rare mais possible via certains exports)
        try {
            LocalDateTime ldt = LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return ldt.atZone(zoneIfNone).toInstant();
        } catch (Exception ignored) {}

        return null;
    }

    // ----------------------- utilitaires existants -----------------------

    private static boolean hasImgExt(String name) {
        int idx = name.lastIndexOf('.');
        if (idx < 0) return false;
        String ext = name.substring(idx + 1).toLowerCase(Locale.ROOT);
        return IMG_EXT.contains(ext);
    }

    private static String formatSliceTitle(Instant instant, double hoursFromT0) {
        ZoneId zone = ZoneId.systemDefault();
        String isoLocalWithOffset = TITLE_FMT.format(instant.atZone(zone));
        String hoursStr = String.format(Locale.ROOT, "%.3f", hoursFromT0);
        return isoLocalWithOffset + "_ = h0 + " + hoursStr + " h";
    }

    private static String formatInstant(Instant instant) {
        return TITLE_FMT.format(instant.atZone(ZoneId.systemDefault()));
    }

    private static int firstIntInName(String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(name);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {}
        }
        return Integer.MAX_VALUE; // met à la fin si pas de nombre
    }

    private static class ImageEntry {
        final File file;
        final Instant instant; // peut être null
        final int numKey;
        ImageEntry(File f, Instant t, int n) { this.file = f; this.instant = t; this.numKey = n; }
    }
}

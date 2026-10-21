package vajra.audio;

import java.nio.file.Files;
import java.nio.file.Path;

/** Writes every synthesised sound out as an ordinary WAV file, so it can be played in any player
 *  (or taken to a sound designer as a reference). Not part of the game:
 *
 *  <pre>java -cp core/build/classes/java/main vajra.audio.SfxExport &lt;folder&gt; [NAME ...]</pre>
 *
 *  With names, only those sounds. Each file is the sound at its full level; in the game it is
 *  played at the sound's own {@link Sfx#gain} and the pilot's volume. */
public final class SfxExport {
    private SfxExport() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: SfxExport <folder> [NAME ...]");
            System.exit(2);
        }
        Path dir = Path.of(args[0]);
        Files.createDirectories(dir);
        int n = 0;
        for (Sfx s : Sfx.values()) {
            if (args.length > 1 && !java.util.Arrays.asList(args).subList(1, args.length).contains(s.name())) continue;
            Files.write(dir.resolve(s.name().toLowerCase() + ".wav"), Wav.encode(Synth.render(s), Synth.RATE));
            n++;
        }
        System.out.println("wrote " + n + " sound(s) to " + dir.toAbsolutePath());
    }
}

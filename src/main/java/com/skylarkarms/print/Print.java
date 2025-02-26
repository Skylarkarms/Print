package com.skylarkarms.print;

import com.skylarkarms.lambdas.Exceptionals;
import com.skylarkarms.lambdas.Funs;
import com.skylarkarms.lambdas.ToStringFunction;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.LongUnaryOperator;

/**
 * A Java Print dependency for easy debugging, uses the default {@link System#out} {@link PrintStream}.
 * <p> Methods like {@link #setAutoFlush(boolean)} will alter the global state of {@link System#out}.
 * <p> Sub-components:
 * <ul>
 *     <li>
 *         {@link Timer}
 *     </li>
 *     <li>
 *         {@link Export}
 *     </li>
 *     <li>
 *         {@link Editor}
 *     </li>
 * </ul>
 * */
public enum Print {

    green("\u001B[32m"),
    purple("\u001B[35m"),
    white("\u001B[37m"),
    red("\u001B[31m"),
    yellow("\u001B[33m"),
    blue("\u001B[34m"),
    cyan("\u001B[36m");

    private static final String ANSI_RESET = "\u001B[0m";

    private final Funs.Unaries.OfString colorWrap;
    @FunctionalInterface
    interface Applier {
        String apply(Funs.Unaries.OfString op, String s);
        default String apply(int startAt, int endAt, Funs.Unaries.OfString op, String s) {
            StackTraceElement[] es = Thread.currentThread().getStackTrace();
            return op.apply(s.concat(
                    Exceptionals.formatStack(startAt, endAt, es)
            ));
        }
        Applier ident = Function::apply;
        Applier stack = (op, s) -> {
            StackTraceElement[] es = Thread.currentThread().getStackTrace();
            return op.apply(s.concat(
                    Exceptionals.formatStack(star_at, STACK_END.ref, es)
            ));
        };
    }

    private Applier printer;

    private static boolean printStack = false;

    /**
     * @return true if the stacktrace is set to be printed by the API.
     * */
    static boolean isStackPrinted() {return printStack;}

    /**
     * Allows the printer to display the call stack.
     * @param value true if the API should display stacks when printing.
     *              false if it shouldn't
     * @implNote
     * <p> To change the format in which the {@link StackTraceElement}s are displayed see
     * {@link Exceptionals#formatStack(int, StackTraceElement[])}
     * <p> To change the range of stacks to be display see {@link Print#star_at}
     * */
    public static void printStack(boolean value) {
        if (printStack != value) {
            Print[] cache = values();
            if (value) {
                for (int i = cache.length - 1; i >= 0; i--) {
                    Print print = cache[i];
                    if (print != null) print.printer = Applier.stack;
                }
            } else {
                for (int i = cache.length - 1; i >= 0; i--) {
                    Print print = cache[i];
                    print.printer = Applier.ident;
                }
            }
            printStack = value;
        }
    }

    /**
     * When setting {@link #printStack} to true, this parameter will redefine the index at which the stack will begin being displayed.
     * @implNote If start_at is greater than the length of the StackTraceElement[], the last index will be displayed instead..
     * */
    private static int star_at = 3;
    /**
     * The non-inclusive last index of the printed StackTraceElement array.
     * */
    private static volatile int stackEnd = Integer.MAX_VALUE;
    private static volatile boolean stackEnd_grabbed = false;
    record STACK_END() {
        static {stackEnd_grabbed = true;}
        static final int ref = stackEnd;
    }

    public static void setStackEnd(int stackEnd) {
        if (stackEnd_grabbed) throw new IllegalStateException("an instance of Print has already been initialized.");
        if (stackEnd < 1) throw new IllegalStateException("Value cannot be lesser than 1.");
        Print.stackEnd = stackEnd;
        if (!printStack) {
            printStack(true);
        }
    }

    /**
     * Sets a {@link #stackEnd} based on a distance from {@link #star_at}, which will start at the default value of: `3`
     * */
    public static void setStackDepth(int depth) { setStackEnd(star_at + depth); }

    public static void printSingleStack() { setStackEnd(star_at + 1); }

    /**
     * Defines the index at which the stack will begin being printed.
     */
    public static void setStackIndex(int at) {
        if (at < 0) throw new IllegalStateException("By the time this code was created (9/5/2024 11:50 pm) indices lesser than 0 didn't exist, so, they are not allowed.");
        star_at = at;
    }

    Print(String color) {
        colorWrap = s -> s.replaceAll("(?m)^", color) + ANSI_RESET;
        printer = isStackPrinted() ? Applier.stack : Applier.ident;

    }

    /**
     * Prints a message
     * @param message the message to be printed
     * */
    public void ln(String message) { System.out.println(printer.apply(colorWrap, message)); }

    /**
     * Prints a message
     * @param message the message to be printed.
     * @param stackDepth the depth of the stack to be printed.
     * */
    public void ln(int stackDepth, String message) { System.out.println(printer.apply(star_at, star_at + stackDepth, colorWrap, message)); }

    /**
     * Prints a message
     * @param message the message to be printed.
     * @param at the index of the stack to be printed.
     * */
    public void stackLn(int at, String message) { System.out.println(printer.apply(at, at + 1, colorWrap, message)); }

    /**
     * Prints a message with the single stack of this call.
     * @param message the message to be printed.
     * */
    public void stackLn(String message) { System.out.println(printer.apply(3, 4, colorWrap, message)); }

    /**
     * Prints a message
     * @param message the message to be printed.
     * @param stackStart the index at which the stack will begin being printed.
     * @param stackEnd the index at which the stack will stop being printed.
     * */
    public void ln(int stackStart, int stackEnd, String message) { System.out.println(printer.apply(stackStart, stackEnd, colorWrap, message)); }

    public String apply(String message) { return printer.apply(colorWrap, message); }

    private static final String d_dot = ": ";

    /**
     * Prints a message with a `TAG` of format:
     * <p> TAG: [Message begins here...]
     * @param TAG the tag to be prefixed.
     * @param message the message to be printed.
     * */
    public void ln(String TAG, String message) {
        System.out.println(printer.apply(colorWrap, TAG + d_dot + message));
    }

    /**
     * Prints a message with a `TAG` of format:
     * <p> TAG: [Message begins here...]
     * @param TAG the tag to be prefixed.
     * @param message the message to be printed.
     * */
    public void ln(int stackDepth, String TAG, String message) {
        System.out.println(printer.apply(star_at, star_at + stackDepth, colorWrap, TAG + d_dot + message));
    }

    /**
     * Variation of {@link #ln(String, String)} that will call {@link String#valueOf(Object)}
     * */
    public void ln(String TAG, Object o) { System.out.println(printer.apply(colorWrap, TAG + d_dot + o)); }

    public void ln(Object o) { System.out.println(printer.apply(colorWrap, String.valueOf(o))); }

    public void ln(long aLong) {
        System.out.println(printer.apply(colorWrap, "long = ".concat(Long.toString(aLong))));
    }

    public void ln(String TAG, int i) { System.out.println(printer.apply(colorWrap, TAG + d_dot + "int = " + i)); }

    public void ln(String TAG, Integer i) {
        System.out.println(printer.apply(colorWrap, TAG + d_dot + "Integer = " + i));
    }

    public void ln(Integer i) { System.out.println(printer.apply(colorWrap, "Integer = " + i)); }

    private static final PrintStream nonFlushed = System.out, flushed = new PrintStream(System.out, true);

    /**
     * Sets the default {@link System#out} {@link PrintStream} to {@link PrintStream#PrintStream(OutputStream, boolean)}.
     * <p> Where: {@code autoflush} boolean = true.
     * @see PrintWriter#PrintWriter(Writer, boolean)
     * */
    public static boolean setAutoFlush(boolean autoFlush) {
        if ((flushed == System.out) == autoFlush) return false;
        System.setOut(autoFlush ? flushed : nonFlushed);
        return true;
    }

    /**
     * Wide {@link String} divisor.
     * */
    public static final String
            divisor =
            """

                     || >>>>>>>> || ** \s
                    ================\s
                     || >>>>>>>> || ** \s""".indent(1);

    private static final String space = "\s";

    public static String depthStack(int depth) {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        return String.join(space, ofSize(
                depth,
                i -> elements[4 + i].toString() + ", \n "
        ));
    }

    private static String[] ofSize(
            int ofLength,
            ToStringFunction.Int xIntFunction
    ) {
        String[] result = new String[ofLength];
        for (int i = 0; i < ofLength; i++) {
            result[i] = xIntFunction.apply(i);
        }
        return result;
    }

    public static String thisStack() { return Thread.currentThread().getStackTrace()[3].toString(); }

    public static final class Nanos {

        public static final class Format {
            public static class UNITS {
                final TimeUnit unit; final LongUnaryOperator mod;
                public static final UNITS sec = new UNITS(TimeUnit.SECONDS, nano -> nano);
                public static final UNITS mill = new UNITS(TimeUnit.MILLISECONDS, nano -> 1000);
                public static final UNITS nano = new UNITS(TimeUnit.NANOSECONDS, nano -> 1_000_000);

                UNITS(TimeUnit unit, LongUnaryOperator mod) {
                    this.unit = unit;
                    this.mod = mod;
                }

                String format(Duration d, long nano) {
                    long res = unit.convert(d) % mod.applyAsLong(nano);
                    return Long.toString(res).concat("[" + of(unit) + "]");
                }
            }
            
            final UNITS[] u;

            Format(UNITS... u) {
                this.u = u;
            }

            public static final Format sec = new Format(UNITS.sec);
            public static final Format mill = new Format(UNITS.mill);
            public static final Format nano = new Format(UNITS.nano);
            public static final Format mill_nan = new Format(UNITS.mill, UNITS.nano);
            public static final Format full = new Format(UNITS.sec, UNITS.mill, UNITS.nano);
        }
        
        final long start = System.nanoTime();
        
        public long lapse() {
            return System.nanoTime() - start;
        }
        
        public String lapse(Format format) {
            return toString(System.nanoTime() - start, format.u);
        }
        
        public static String toString(long nanoseconds, Format nanoFormat) {
            return toString(nanoseconds, nanoFormat.u);
        }
        
        public static String toString(long nanos, Format.UNITS... units) {
            int l = units.length;
            Duration d = Duration.ofNanos(nanos);
            StringBuilder b = new StringBuilder(l);
            int l_i = l - 1;
            if (l > 1) {
                for (int i = 0; i < l_i; i++) {
                    b.append(units[i].format(d, nanos)).append(": ");
                }
            }
            b.append(units[l_i].format(d, nanos));
            return b.toString();
        }
        public static String toString(long nanos) {
            return toString(nanos, Format.full.u);
        }

        static String of(TimeUnit unit) {
            return switch (unit) {
                case SECONDS -> "secs";
                case MILLISECONDS -> "millis";
                case NANOSECONDS -> "nanos";
                default -> throw new IllegalStateException(unit.name());
            };
        }
    }

    /**
     * Component that helps is the measure of nanos via
     * atomic CAS-sing.
     * */
    public static final class Timer {
        private static int id = 0;
        private volatile long begin;
        private final Object lock = new Object();
        private volatile long last;
        private final Nanos.Format nanoFormat;
        private final Print color;
        private final int chronoId = id++;


        private static final VarHandle LAST;

        static {
            try {
                LAST = MethodHandles.lookup().findVarHandle(Timer.class, "last", long.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private void ln(
                String prefix, long toFormat
        ) {
            color.ln(
                    prefix + " at (chrono = " + chronoId + ")...\n" + Nanos.toString(toFormat, nanoFormat).indent(3)
            );
        }

        /**
         * Prints the elapsed time since a {@link #start()} was called.
         * */
        public void elapsed() {
            System.out.println(color.printer.apply(color.colorWrap,
                    "Elapsed" + " at (chrono = " + chronoId + ")...\n" + Nanos.toString(System.nanoTime() - begin, nanoFormat).indent(3)
            ));
        }

        /**
         * @return the nano long values since a {@link #start()} was called.
         * */
        public long elapsedNanos() {
            long res = System.nanoTime() - begin;
            assert begin != 0 : "Must have called 'start()' or 'silentStart()' first.";
            return res;
        }

        /**
         * Prints the time passed since last time this method was called
         * OR since a {@link #start()} was first called.
         * The operation is performed atomically
         * */
        public void lap() {
            long prev = last, now = System.nanoTime();
            assert last != 0 : "Must have called .start()";
            long lap = now - prev;
            if (LAST.compareAndSet(this, prev, now)) {
                ln(
                        "Lapsed", lap);
            }
        }

        /**
         * {@code `synchronized`} version of {@link #lap()}
         * */
        public void syncLap() {
            synchronized (lock) {
                long prev = last;
                long lap = System.nanoTime() - prev;
                last = lap;
                ln(
                        "Sync lapsed", lap);
            }
        }

        static final SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a");

        /**
         * Default implementation of {@link #Timer(Nanos.Format, Print, boolean)}.
         * <p> Where:
         * <ul>
         *     <li>
         *         {@code start} = {@code false}
         *     </li>
         * </ul>
         * */
        public Timer(Nanos.Format nanoFormat, Print color) {
            this(nanoFormat, color, false);
        }

        /**
         * Mina constructor for the {@link Timer} class
         * @param start, set's the chronometer going when this constructor is called with true.
         * @param color the color of the print
         * @param nanoFormat the {@link Nanos.Format} by which the nanos will be displayed.
         * */
        public Timer(Nanos.Format nanoFormat, Print color, boolean start) {
            this.color = color;
            this.nanoFormat = nanoFormat;
            if (start) start();
        }

        /**
         * Default implementation of {@link #Timer(Nanos.Format, Print)}
         * <p> Where:
         * <ul>
         *     <li>
         *         {@link Nanos.Format} = {@link Nanos.Format#full}
         *     </li>
         * </ul>
         * */
        public Timer(Print color) {
            this(Nanos.Format.full, color);
        }

        /**
         * Default implementation of {@link #Timer(Nanos.Format, Print, boolean)}
         * <p> Where:
         * <ul>
         *     <li>
         *         {@link Nanos.Format} = {@link Nanos.Format#full}
         *     </li>
         * </ul>
         * */
        Timer(Print color, boolean start) { this(Nanos.Format.full, color, start); }

        /**
         * Will set the starting time ({@link #begin}) to the exact {@link System#nanoTime()} when this method is being called.
         * */
        public void start() {
            this.begin = System.nanoTime();
            this.last = begin;
            System.out.println(
                    color.printer.apply(
                            color.colorWrap,
                            "\n >>> Timer " + chronoId + ", begins at = " +
                                    "\n" + sdf.format(new Date(System.currentTimeMillis())).indent(3)
            ));
        }

        /**
         * Will trigger a {@link #start()} without printing
         * */
        public void silentStart() {
            this.begin = System.nanoTime();
            this.last = begin;
        }

        /**
         * Without printing, this method will set the starting time ({@link #begin}) to the exact {@link System#nanoTime()}.
         * */
        public String startText() {
            this.begin = System.nanoTime();
            this.last = begin;
            return color.printer.apply(
                    color.colorWrap,
                    "\n >>> Timer " + chronoId + ", begins at = " +
                            "\n" + sdf.format(new Date(System.currentTimeMillis())).indent(3)
            );
        }

        /**
         * @return a {@link Timer} object that implements a {@link #Timer(Print, boolean)} constructor,
         * <p> Where:
         * <ul>
         *     <li>
         *         {@code start} = true
         *     </li>
         * </ul>
         * */
        public static Timer begin(Print color) { return new Timer(color, true); }
    }

    /**
     * A <a href="package-summary.html">functional interface</a> for exporting data to a file.
     */
    @FunctionalInterface
    interface Exporter {
        /**
         * Saves the provided headers and data to a file in the specified directory with the given file name.
         *  @param DIRECTORY the directory where the file will be saved
         * @param fileName the name of the file without the extension
         * @param overwrite Directs the {@link BufferedWriter} whether the file to be saved in the DIRECTORY should replace any files of the same name.
         *                <p> If {@code `false`} an autogenerated suffix will be appended to the original name in the pattern:
         *               <p> X + `_copy` + `Y` where Y is an integer representing
         *                the copy number of the file will be created for the file saved.
         *                <p> If {@code `true`}, the Exporter will attempt to overwrite the file, unless the device prevents this action,
         *                in which case an Exception specifying the reason why, will be thrown.
         * @param data a 2D array representing the data rows to be written to the file. Each sub-array should match the length of the headers array
         *
         * <p>Example usage:</p>
         * <pre>{@code
         * public class ExporterExample {
         *     public static void main(String[] args) {
         *         String directory = "C:\\exports";
         *         String fileName = "example";
         *         String[][] data = {
         *             {"ID", "Name", "Age"},
         *             {"1", "Alice", "30"},
         *             {"2", "Bob", "25"},
         *             {"3", "Charlie", "35"}
         *         };
         *
         *         Export.to_csv.save(directory, fileName, true, data);
         *     }
         * }
         * }</pre>
         */
        void save(
                String DIRECTORY,
                String fileName,
                boolean overwrite,
                String[]... data
                );

        default void save(
                String DIRECTORY,
                String fileName,
                String[]... data
        ) {
            save(DIRECTORY, fileName, false, data);
        }
    }

    /**
     * Standard set of {@link Exporter} file extensions implementations.
     * @see Exporter#save(String, String, boolean, String[][])
     * */
    public enum Export implements Exporter {
        to_csv(
                ".csv"
        );

        final String type;

        Export(String type) { this.type = type; }

        record creatorRes(String finalFileName, BufferedWriter w){}
        static String getFullPath(String DIRECTORY, String fileName, String type) { return DIRECTORY + "\\" + fileName + type; }

        creatorRes createWriter(
                String DIRECTORY,
                String fileName,
                boolean overwrite,
                String type) throws IOException {
            BufferedWriter csvWriter;
            String finalPath = getFullPath(DIRECTORY, fileName, type);
            if (!overwrite) {
                if (Files.exists(Paths.get(finalPath))) {
                    fileName = fileName.concat("_copy");
                    if (Files.exists(Paths.get(finalPath = getFullPath(DIRECTORY, fileName, type)))) {
                        String tryName;
                        int i = 0;
                        do {
                            i++;
                            tryName = fileName.concat(Integer.toString(i));
                        } while (Files.exists(Paths.get(finalPath = getFullPath(DIRECTORY, tryName, type))));
                    }
                }
            }
            csvWriter = new BufferedWriter(new FileWriter(finalPath));
            return new creatorRes(fileName + type, csvWriter);
        }

        @Override
        public void save(
                String DIRECTORY,
                String fileName,
                boolean overwrite,
                String[]... data) {
            BufferedWriter csvWriter = null;
            try {
                creatorRes res = createWriter(DIRECTORY, fileName, overwrite, type);
                csvWriter = res.w;

                // Write data
                for (String[] row : data) {
                    csvWriter.write(String.join(",", row));
                    csvWriter.newLine();
                }

                // Flushing can be controlled manually
                csvWriter.flush();
                System.out.println("CSV file [" + res.finalFileName + "] "
                        + "\n created successfully at: " + DIRECTORY);

            } catch (IOException | IllegalStateException e) {
                e.printStackTrace();
            } finally {
                // Ensure the writer is closed
                if (csvWriter != null) {
                    try {
                        csvWriter.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * File Editor for the given formats:
     * <ul>
     *     <li>
     *         {@link #HTML},
     *     </li>
     *     <li>
     *         {@link #TXT},
     *     </li>
     *     <li>
     *         {@link #XML},
     *     </li>
     * </ul>
     * <p> With methods such as:
     * <ul>
     *     <li>{@link #editLine(String, String, Line...)}</li>
     * </ul>
     * */
    public enum Editor {
        HTML(".html"),
        TXT(".txt"),
        XML(".xml");

        private final String extension;

        Editor(String extension) {
            this.extension = extension;
        }

        public String getExtension() {
            return extension;
        }

        /**
         * The object to define the line that will be swapped from the original file.
         * @param number the line number to be changed
         * @param content the new content to be swapped in the specified line.
         * */
        public record Line(int number, String content) {}

        /**
         * Edits specific lines in a file and writes the modified content to a new file with "_copy" appended to the original file name.
         * <p>
         * This method reads the original file line by line, replaces the specified lines with new content, and writes the result to a new file.
         * The new file will have the same name as the original file with "_copy" appended before the file extension.
         * </p>
         *
         * @param path      the directory path where the file is located
         * @param fileName  the name of the file without the extension
         * @param lines     varargs parameter of {@link Line} records, each containing the line number and new content
         *
         * <p>
         * Example usage:
         * </p>
         * <pre>{@code
         * public static void main(String[] args) {
         *     Editor editor = Editor.HTML;
         *     editor.editLine("path/to/your", "file",
         *         new Line(10, "<newTag>New Content</newTag>"),
         *         new Line(20, "<anotherTag>Another Content</anotherTag>")
         *     );
         * }
         * }</pre>
         */
        public void editLine(String path, String fileName, Line... lines) {
            Path filePath = Paths.get(path, fileName + this.extension);
            Path newFilePath = Paths.get(path, fileName + "_copy" + this.extension);

            try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8);
                 BufferedWriter writer = Files.newBufferedWriter(newFilePath, StandardCharsets.UTF_8)) {

                String line;
                int lineNumber = 0;

                int curL = 0, prevLN = -1;
                Line line1;
                while ((line = reader.readLine()) != null) {
                    while (curL < lines.length && lineNumber == (line1 = lines[curL]).number) {
                        assert prevLN < line1.number : "Lines are not in ascending order";
                        prevLN = line1.number;
                        line = line1.content;
                        curL++;
                    }
                    writer.write(line);
                    writer.newLine();
                    lineNumber++;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

package be.hogent.tarsos.apps;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.MidiDevice.Info;

import be.hogent.tarsos.midi.MidiCommon;
import be.hogent.tarsos.util.Configuration;

/**
 * This is the starting point of the Tarsos application suite. It's main task is
 * to start other applications and to maintain state. Creating the needed
 * directories, checking the runtime,... and also passing, minimal parsing and
 * checking arguments are tasks for this class.
 * @author Joren Six
 */
public final class Tarsos {

    /**
     * A map of applications, maps the name of the application to the instance.
     */
    private final transient Map<String, AbstractTarsosApp> applications;

    private final Logger log;
    /**
     * Create a new Tarsos application instance.
     */
    private Tarsos() {
        applications = new HashMap<String, AbstractTarsosApp>();
        // create needed directories, ...
        Configuration.createRequiredDirectories();

        try {
            // configure logging
            final String propertiesFile = "/be/hogent/tarsos/util/logging.properties";
            final InputStream stream = Tarsos.class.getResourceAsStream(propertiesFile);
            LogManager.getLogManager().readConfiguration(stream);
        } catch (final SecurityException e) {
            e.printStackTrace();
            // a bit hard to log, logging is not working yet :(
        } catch (final IOException e) {
            e.printStackTrace();
            // a bit hard to log, logging is not working yet :(
        }
        log = Logger.getLogger(Tarsos.class.getName());
    }

    /**
     * Register a Tarsos application.
     * @param name
     *            The name (parameter) of the Tarsos application. The parameter
     *            is used in <code>java -jar tarsos.jar application</code>.
     * @param application
     *            The instance that represents the Tarsos application.
     */
    public void registerApplication(final String name, final AbstractTarsosApp application) {
        applications.put(name, application);
    }

    /**
     * @param arguments
     *            The arguments for the program.
     */
    public void run(final String... arguments) {
        if (arguments.length == 0) {
            print("java -jar tarsos.jar subcommand [subcommand options]");
        } else {
            final String subcommand = arguments[0];
            String[] subcommandArgs;
            if (arguments.length > 1) {
                subcommandArgs = Arrays.copyOfRange(arguments, 1, arguments.length);
            } else {
                subcommandArgs = new String[0];
            }
            if (applications.containsKey(subcommand)) {
                applications.get(subcommand).run(subcommandArgs);
            } else {
                print("Unknown subcommand. Valid subcommands:");
                for (final String key : applications.keySet()) {
                    print("\t" + key);
                }
            }
        }
    }

    /**
     * The only instance of Tarsos.
     */
    private static Tarsos tarsosInstance;

    /**
     * Thread safe singleton implementation.
     * @return The only instance of the Tarsos application.
     */
    public static Tarsos getInstance() {
        synchronized (Tarsos.class) {
            if (tarsosInstance == null) {
                tarsosInstance = new Tarsos();
            }
        }

        return tarsosInstance;
    }

    /**
     * @param args
     *            The arguments consist of a subcommand and options for the
     *            subcommand. E.g.
     *            <pre>
     * java -jar annotate --in blaat.wav
     * java -jar annotate -i blaat.wav
     * </pre>
     */
    public static void main(final String... args) {
        final Tarsos instance = Tarsos.getInstance();

        final List<AbstractTarsosApp> applicationList = new ArrayList<AbstractTarsosApp>();
        applicationList.add(new Annotate());
        applicationList.add(new PitchTable());
        applicationList.add(new MidiToWav());
        applicationList.add(new AudioToScala());
        applicationList.add(new DetectPitch());
        applicationList.add(new AnnotationSynth());
        applicationList.add(new PowerExtractor());
        applicationList.add(new TuneMidiSynth());


        for (final AbstractTarsosApp application : applicationList) {
            instance.registerApplication(application.name(), application);
        }

        instance.run(args);
    }

    /**
     * Prints information to standard out.
     * @param info
     *            the information to print.
     */
    private void print(final String info) {
        final PrintStream standardOut = System.out;
        standardOut.println(info);
    }

    /**
     * Choose a MIDI device using a CLI. If an invalid device number is given
     * the user is requested to choose another one.
     * 
     * @param inputDevice
     *            is the MIDI device needed for input of events? E.G. a keyboard
     * @param outputDevice
     *            is the MIDI device needed to send events to? E.g. a (software)
     *            synthesizer.
     * @return the chosen MIDI device
     */
    public static MidiDevice chooseDevice(final boolean inputDevice, final boolean outputDevice) {
        MidiDevice device = null;
        try {
            // choose MIDI input device
            MidiCommon.listDevices(inputDevice, outputDevice);
            final String deviceType = (inputDevice ? " IN " : "") + (outputDevice ? " OUT " : "");
            println("Choose the MIDI" + deviceType + "device: ");
            final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            final int deviceIndex = Integer.parseInt(br.readLine());
            println("");
            final Info midiDeviceInfo = MidiSystem.getMidiDeviceInfo()[deviceIndex];

            device = MidiSystem.getMidiDevice(midiDeviceInfo);
            if ((device.getMaxTransmitters() == 0 == inputDevice)
                    && (device.getMaxReceivers() == 0 == outputDevice)) {
                println("Invalid choise, please try again");
                device = chooseDevice(inputDevice, outputDevice);
            }
        } catch (final NumberFormatException e) {
            println("Invalid number, please try again");
            device = chooseDevice(inputDevice, outputDevice);
        } catch (final IOException e) {
            getInstance().log.log(Level.SEVERE, "Exception while reading from STD IN.", e);
        } catch (final MidiUnavailableException e) {
            println("The device is not available ( " + e.getMessage()
                    + " ), please choose another device.");
            device = chooseDevice(inputDevice, outputDevice);
        } catch (final ArrayIndexOutOfBoundsException e) {
            println("Number out of bounds, please try again");
            device = chooseDevice(inputDevice, outputDevice);
        }
        return device;
    }

    /**
     * Prints info to a stream (console).
     * 
     * @param info
     *            The information to print.
     */
    public static void println(final String info) {
        getInstance().print(info);
    }
}

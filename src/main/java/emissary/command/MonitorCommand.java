package emissary.command;

import emissary.client.EmissaryClient;
import emissary.client.response.BaseResponseEntity;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public abstract class MonitorCommand<T extends BaseResponseEntity> extends HttpCommand {

    static final Logger LOG = LoggerFactory.getLogger(MonitorCommand.class);

    public static final String COMMAND_NAME = "MonitorCommand";

    private final Object lock = new Object();

    @Parameter(names = {"--mon"}, description = "runs the agents command in monitor mode, executing every 30 seconds by default")
    private boolean monitor = false;

    @Parameter(names = {"-i", "--interval"}, description = "how many seconds to wait between each endpoint call")
    private int sleepInterval = 30;

    @Parameter(names = {"--cluster"}, description = "sets endpoint to clustered mode")
    private boolean clustered = false;

    public boolean getMonitor() {
        return monitor;
    }

    public int getSleepInterval() {
        return sleepInterval;
    }

    public boolean getClustered() {
        return clustered;
    }

    public abstract T sendRequest(EmissaryClient client, String endpoint);

    public abstract String getTargetEndpoint();

    @Override
    public void run(JCommander jc) {
        setup();
        try {
            do {
                LOG.info(new Date().toString());
                collectEndpointData();
                if (getMonitor()) {
                    TimeUnit.SECONDS.sleep(getSleepInterval());
                }
            } while (getMonitor());
        } catch (InterruptedException e) {
            // nothing to log here, command was terminated
        }
    }

    private void collectEndpointData() {
        EmissaryClient client = new EmissaryClient();

        T entity = sendRequest(client, buildEndpoint(getHost(), getPort()));
        try {
            if (getClustered()) {
                sendClusterRequests(client, entity);
            }
        } catch (IOException e) {
            LOG.error("Problem generating peer list. Something is very wrong.");
        }

        displayEntityResults(entity);
    }

    private void sendClusterRequests(final EmissaryClient client, final T entity) throws IOException {
        PeersCommand.getPeers(getHostAndPort(), true).parallelStream().forEach(hostAndPort -> {
            try {
                String endpoint = buildEndpoint(hostAndPort);
                T response = sendRequest(client, endpoint);
                synchronized (lock) {
                    entity.append(response);
                }
            } catch (Exception e) {
                LOG.error("Problem hitting agents endpoint: {}\n{}", hostAndPort, e.getMessage());
                synchronized (lock) {
                    entity.addError(e.getMessage());
                }
            }
        });
    }

    // Here as a hook in case commands have summarize/custom display options
    protected void displayEntityResults(T entity) {
        entity.dumpToConsole();
        for (String error : entity.getErrors()) {
            System.err.print(error);
        }
    }

    private String buildEndpoint(final String host, final int port) {
        return buildEndpoint(host + ":" + port);
    }

    private String buildEndpoint(final String hostAndPort) {
        return getScheme() + "://" + hostAndPort + "/" + getTargetEndpoint();
    }

}

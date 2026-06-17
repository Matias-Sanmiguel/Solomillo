package dev.solomillo.ml;

import dev.solomillo.core.AppProperties;
import dev.solomillo.domain.EstadoPartido;
import dev.solomillo.domain.Partido;
import dev.solomillo.repository.ModeloPredictivoRepository;
import dev.solomillo.repository.PartidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import weka.classifiers.Classifier;
import weka.classifiers.functions.LinearRegression;
import weka.classifiers.functions.Logistic;
import weka.core.Instances;
import weka.core.SerializationHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

@Service
public class MlTrainer {

    private static final int MIN_PARTIDOS = 20;

    private final ModeloPredictivoRepository repo;
    private final PartidoRepository partidoRepo;
    private final AppProperties props;

    public MlTrainer(ModeloPredictivoRepository repo, PartidoRepository partidoRepo, AppProperties props) {
        this.repo = repo;
        this.partidoRepo = partidoRepo;
        this.props = props;
    }

    @Transactional
    public ModeloPredictivo entrenarResultado() throws Exception {
        List<Partido> finalizados = partidoRepo.findByEstadoOrderByFechaHoraAsc(EstadoPartido.FINALIZADO)
                .stream().sorted(Comparator.comparing(Partido::getFechaHora)).toList();
        if (finalizados.size() < MIN_PARTIDOS) {
            throw new IllegalStateException("Datos insuficientes: " + finalizados.size()
                    + " partidos finalizados (minimo " + MIN_PARTIDOS + ").");
        }

        Instances data = FeatureExtractor.buildHeader();
        for (Partido p : finalizados) {
            data.add(new weka.core.DenseInstance(1.0, FeatureExtractor.features(p, finalizados)));
        }

        int split = (int) (data.numInstances() * 0.8);
        Instances train = new Instances(data, 0, split);
        Instances test = new Instances(data, split, data.numInstances() - split);

        Logistic clf = new Logistic();
        clf.setMaxIts(500);
        clf.buildClassifier(train);

        double correct = 0, logLoss = 0, brier = 0;
        int n = test.numInstances();
        for (int i = 0; i < n; i++) {
            var inst = test.instance(i);
            double[] probs = clf.distributionForInstance(inst);
            int real = (int) inst.classValue();
            if (clf.classifyInstance(inst) == real) correct++;
            double p = Math.max(1e-15, probs[real]);
            logLoss += -Math.log(p);
            for (int k = 0; k < probs.length; k++) {
                double y = k == real ? 1.0 : 0.0;
                brier += Math.pow(probs[k] - y, 2);
            }
        }
        double acc = n > 0 ? correct / n : 0;
        double ll = n > 0 ? logLoss / n : 0;
        double br = n > 0 ? brier / n : 0;

        return persistir("resultado_partido", "clasificacion", clf, acc, ll, br);
    }

    public ModeloPredictivo entrenarRendimiento() throws Exception {
        Instances data = DatasetGenerator.generarRendimiento(2000, 42);
        int split = (int) (data.numInstances() * 0.8);
        Instances train = new Instances(data, 0, split);
        Instances test = new Instances(data, split, data.numInstances() - split);

        LinearRegression reg = new LinearRegression();
        reg.buildClassifier(train);

        double ssTot = 0, ssRes = 0;
        double mean = test.stream().mapToDouble(i -> i.classValue()).average().orElse(0);
        for (int i = 0; i < test.numInstances(); i++) {
            double pred = reg.classifyInstance(test.instance(i));
            double actual = test.instance(i).classValue();
            ssRes += Math.pow(actual - pred, 2);
            ssTot += Math.pow(actual - mean, 2);
        }
        double r2 = ssTot > 0 ? 1 - ssRes / ssTot : 0;

        return persistir("rendimiento_jugador", "regresion", reg, r2, 0, 0);
    }

    private ModeloPredictivo persistir(String nombre, String tipo, Classifier clf,
                                       double acc, double logLoss, double brier) throws Exception {
        Path dir = Path.of(props.modelsDir);
        Files.createDirectories(dir);

        int version = repo.findFirstByNombreOrderByVersionDesc(nombre)
                .map(m -> m.getVersion() + 1).orElse(1);
        Path ruta = dir.resolve(nombre + "_v" + version + ".model");
        SerializationHelper.write(ruta.toString(), clf);

        repo.findByNombre(nombre).forEach(m -> { m.setActivo(false); repo.save(m); });

        var modelo = new ModeloPredictivo();
        modelo.setNombre(nombre);
        modelo.setVersion(version);
        modelo.setTipo(tipo);
        modelo.setRuta(ruta.toString());
        modelo.setAccuracy(acc);
        modelo.setLogLoss(logLoss);
        modelo.setBrier(brier);
        modelo.setActivo(true);
        return repo.save(modelo);
    }
}

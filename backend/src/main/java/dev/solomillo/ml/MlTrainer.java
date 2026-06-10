package dev.solomillo.ml;

import dev.solomillo.core.AppProperties;
import dev.solomillo.repository.ModeloPredictivoRepository;
import org.springframework.stereotype.Service;
import weka.classifiers.functions.Logistic;
import weka.classifiers.functions.LinearRegression;
import weka.core.Instances;
import weka.core.SerializationHelper;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class MlTrainer {

    private final ModeloPredictivoRepository repo;
    private final AppProperties props;

    public MlTrainer(ModeloPredictivoRepository repo, AppProperties props) {
        this.repo = repo;
        this.props = props;
    }

    public ModeloPredictivo entrenarResultado() throws Exception {
        Instances data = DatasetGenerator.generarPartidos(2000, 42);
        int split = (int) (data.numInstances() * 0.8);
        Instances train = new Instances(data, 0, split);
        Instances test = new Instances(data, split, data.numInstances() - split);

        Logistic clf = new Logistic();
        clf.setMaxIts(500);
        clf.buildClassifier(train);

        double correct = 0;
        for (int i = 0; i < test.numInstances(); i++) {
            if (clf.classifyInstance(test.instance(i)) == test.instance(i).classValue()) correct++;
        }
        double acc = correct / test.numInstances();

        return persistir("resultado_partido", "clasificacion", clf, acc);
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

        return persistir("rendimiento_jugador", "regresion", reg, r2);
    }

    private ModeloPredictivo persistir(String nombre, String tipo, Object clf, double score) throws Exception {
        Path dir = Path.of(props.modelsDir);
        Files.createDirectories(dir);

        int version = repo.findFirstByNombreOrderByVersionDesc(nombre)
                .map(m -> m.getVersion() + 1).orElse(1);
        Path ruta = dir.resolve(nombre + "_v" + version + ".model");
        SerializationHelper.write(ruta.toString(), clf);

        repo.findByNombre(nombre).forEach(m -> { m.setActivo(false); repo.save(m); });

        var modelo = new ModeloPredictivo();
        modelo.setNombre(nombre); modelo.setVersion(version); modelo.setTipo(tipo);
        modelo.setRuta(ruta.toString()); modelo.setAccuracy(score); modelo.setActivo(true);
        return repo.save(modelo);
    }
}

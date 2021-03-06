package org.omegabyte.gaboom.transforms.model;

import org.apache.beam.sdk.coders.KvCoder;
import org.apache.beam.sdk.coders.SerializableCoder;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.join.CoGbkResult;
import org.apache.beam.sdk.transforms.join.CoGroupByKey;
import org.apache.beam.sdk.transforms.join.KeyedPCollectionTuple;
import org.apache.beam.sdk.values.*;
import org.omegabyte.gaboom.Individual;
import org.omegabyte.gaboom.Individuals;
import org.omegabyte.gaboom.NBaseItem;
import org.omegabyte.gaboom.SelectIndividuals;
import org.omegabyte.gaboom.transforms.Crossover;
import org.omegabyte.gaboom.transforms.Mutate;
import org.omegabyte.gaboom.transforms.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;

public class GenerateOffspringsTransform<GenomeT extends Serializable> extends PTransform<PCollection<KV<String, SelectIndividuals<GenomeT>>>, PCollection<KV<String, Individuals<GenomeT>>>> {
    private final Select.SelectNoIndexTransform<GenomeT> selectTransform;
    private final Crossover.CrossoverTransform<GenomeT> crossoverTransform;
    private final Mutate.MutateTransform<GenomeT> mutateTransform;

    public GenerateOffspringsTransform(Select.SelectFn<GenomeT> selectFn, Crossover.CrossoverTransform<GenomeT> crossoverTransform, Mutate.MutateTransform<GenomeT> mutateTransform) {
        this.selectTransform = Select.as(selectFn);
        this.crossoverTransform = crossoverTransform;
        this.mutateTransform = mutateTransform;
    }

    public static <GenomeT extends Serializable> GenerateOffspringsTransform<GenomeT> of(Select.SelectFn<GenomeT> selectFn, Crossover.CrossoverTransform<GenomeT> crossoverTransform, Mutate.MutateTransform<GenomeT> mutateTransform) {
        return new GenerateOffspringsTransform<>(selectFn, crossoverTransform, mutateTransform);
    }

    static class SelectParentsFn<GenomeT extends Serializable> extends DoFn<KV<String, SelectIndividuals<GenomeT>>, KV<String, SelectIndividuals<GenomeT>>> {
        private final TupleTag<KV<String, NBaseItem>> nBaseItemIndexTupleTag;

        public SelectParentsFn(TupleTag<KV<String, NBaseItem>> nBaseItemIndexTupleTag) {
            this.nBaseItemIndexTupleTag = nBaseItemIndexTupleTag;
        }

        @ProcessElement
        public void processElement(ProcessContext c) {
            String key = c.element().getKey();
            SelectIndividuals<GenomeT> selectIndividuals = c.element().getValue();
            Random rng = selectIndividuals.getRandomGenerator();

            c.output(nBaseItemIndexTupleTag, KV.of(key, new NBaseItem(rng.nextLong(), selectIndividuals.getN())));
            for (int i = 0; i < (selectIndividuals.getN()+1)/2; i++) {
                c.output(KV.of(key, new SelectIndividuals<>(rng.nextLong(), selectIndividuals.getIndividuals(), 2)));
            }
        }
    }

    static class GenerateOffspringsFn<GenomeT extends Serializable> extends DoFn<KV<String, CoGbkResult>, KV<String, Individuals<GenomeT>>> {
        private static final Logger logger = LoggerFactory.getLogger(GenerateOffspringsFn.class);

        private final TupleTag<NBaseItem> nBaseItemTupleTag;
        private final TupleTag<Individuals<GenomeT>> individualsTupleTag;

        public GenerateOffspringsFn(TupleTag<NBaseItem> nBaseItemTupleTag, TupleTag<Individuals<GenomeT>> individualsTupleTag) {
            this.nBaseItemTupleTag = nBaseItemTupleTag;
            this.individualsTupleTag = individualsTupleTag;
        }

        @ProcessElement
        public void processElement(ProcessContext c) {
            String key = c.element().getKey();
            CoGbkResult result = c.element().getValue();

            NBaseItem nBaseItem = result.getOnly(nBaseItemTupleTag);
            List<Individual<GenomeT>> individualList = new ArrayList<>();
            result.getAll(individualsTupleTag).forEach(individuals -> individualList.addAll(individuals.getIndividuals()));
            Collections.sort(individualList, Comparator.comparing(Individual::getId));

            if (individualList.size() < nBaseItem.getN()) {
                logger.error("Not enough individuals to populate, id={}", key);
                return;
            }

            Individuals<GenomeT> individuals = new Individuals<>(nBaseItem.getSeed());
            individuals.getIndividuals().addAll(individualList.subList(0, nBaseItem.getN()));
            c.output(KV.of(key, individuals));
        }
    }

    @Override
    public PCollection<KV<String, Individuals<GenomeT>>> expand(PCollection<KV<String, SelectIndividuals<GenomeT>>> input) {
        // Select pairs
        TupleTag<KV<String, NBaseItem>> nBaseItemAtKeyTT = new TupleTag<>();
        TupleTag<KV<String, SelectIndividuals<GenomeT>>> selectPairsAtKeyTT = new TupleTag<>();
        PCollectionTuple result = input.apply(ParDo.of(new SelectParentsFn<GenomeT>(nBaseItemAtKeyTT))
                .withOutputTags(selectPairsAtKeyTT, TupleTagList.of(nBaseItemAtKeyTT)));

        PCollection<KV<String, NBaseItem>> nBaseItemAtKey = result.get(nBaseItemAtKeyTT)
                .setCoder(KvCoder.of(StringUtf8Coder.of(), SerializableCoder.of(NBaseItem.class)));

        // Create offspring
        PCollection<KV<String, Individuals<GenomeT>>> offsprings = result.get(selectPairsAtKeyTT)
                .apply(selectTransform)
                .apply(crossoverTransform)
                .apply(mutateTransform);

        // Combine offspring
        TupleTag<NBaseItem> nBaseItemTT = new TupleTag<>();
        TupleTag<Individuals<GenomeT>> offspringsTT = new TupleTag<>();
        return KeyedPCollectionTuple.of(offspringsTT, offsprings).and(nBaseItemTT, nBaseItemAtKey)
                .apply(CoGroupByKey.create())
                .apply(ParDo.of(new GenerateOffspringsFn<>(nBaseItemTT, offspringsTT)));
    }
}
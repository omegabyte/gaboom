package org.omegabyte.gaboom.transforms;

import org.apache.beam.runners.dataflow.repackaged.com.google.common.collect.ImmutableList;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionView;
import org.omegabyte.gaboom.CrossoverIndividuals;
import org.omegabyte.gaboom.Individual;
import org.omegabyte.gaboom.Individuals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Crossover {

    public abstract static class CrossoverFn<GenomeT> extends DoFn<KV<String, CrossoverIndividuals<GenomeT>>, KV<String, Individuals<GenomeT>>> {
        private double crossRate = 0;

        public void setCrossRate(double crossRate) {
            this.crossRate = crossRate;
        }

        public abstract GenomeT crossover(ProcessContext context, Random random, GenomeT p1, GenomeT p2);

        @ProcessElement
        public void processElement(ProcessContext c) {
            String key = c.element().getKey();
            Individual<GenomeT> p1 = c.element().getValue().getP1();
            Individual<GenomeT> p2 = c.element().getValue().getP2();
            Random rng = c.element().getValue().getRandomGenerator();

            Individuals<GenomeT> offspring = new Individuals<>(rng.nextLong());
            if (crossRate > 0 && rng.nextDouble() <= crossRate) {
                offspring.getIndividuals().add(new Individual<>(rng, crossover(c, rng, p1.getGenome(), p2.getGenome())));
                offspring.getIndividuals().add(new Individual<>(rng, crossover(c, rng, p1.getGenome(), p2.getGenome())));
            } else {
                offspring.getIndividuals().add(p1);
                offspring.getIndividuals().add(p2);
            }
            c.output(KV.of(key, offspring));
        }
    }

    public static class CrossoverTransform<GenomeT> extends PTransform<PCollection<KV<String, CrossoverIndividuals<GenomeT>>>, PCollection<KV<String, Individuals<GenomeT>>>> {
        private final CrossoverFn<GenomeT> fn;
        private final List<PCollectionView<?>> sideInputs;

        public CrossoverTransform(CrossoverFn<GenomeT> fn, List<PCollectionView<?>> sideInputs) {
            this.fn = fn;
            this.sideInputs = sideInputs;
        }

        public CrossoverTransform withSideInputs(PCollectionView... sideInputs) {
            return this.withSideInputs((Iterable) Arrays.asList(sideInputs));
        }

        public CrossoverTransform withSideInputs(Iterable<? extends PCollectionView<?>> sideInputs) {
            List list = ImmutableList.builder().addAll(this.sideInputs).addAll(sideInputs).build();
            return new CrossoverTransform<>(fn, list);
        }

        public CrossoverTransform<GenomeT> withCrossRate(double crossRate) {
            CrossoverTransform ct = new CrossoverTransform<>(fn, sideInputs);
            ct.fn.setCrossRate(crossRate);
            return ct;
        }

        @Override
        public PCollection<KV<String, Individuals<GenomeT>>> expand(PCollection<KV<String, CrossoverIndividuals<GenomeT>>> input) {
            return input.apply(ParDo.of(fn).withSideInputs(sideInputs));
        }
    }

    public static <GenomeT> CrossoverTransform<GenomeT> as(CrossoverFn<GenomeT> fn) {
        return new CrossoverTransform<>(fn, Collections.emptyList());
    }
}
package org.omegabyte.gaboom;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.text.RandomStringGenerator;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

public class Population<GenomeT> extends Individuals<GenomeT> {
    private final String id;
    private final long created = new Date().getTime();
    private int generations = -1;

    public Population(String id) {
        super(0);
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public List<Individual<GenomeT>> getIndividuals() {
        return individuals;
    }

    public long getAge() {
        return new Date().getTime() - created;
    }

    public int getGenerations() {
        return generations;
    }

    public void update(Individuals<GenomeT> individuals) {
        setSeed(individuals.getSeed());
        this.individuals = individuals.getIndividuals();
        this.generations++;
    }
}
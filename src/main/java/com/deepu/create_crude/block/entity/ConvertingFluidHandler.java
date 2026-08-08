package com.deepu.create_crude.block.entity;

import com.deepu.create_crude.ModFluids;
import com.deepu.create_crude.SulfurFluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class ConvertingFluidHandler implements IFluidHandler {
    private final IFluidHandler delegate;

    public ConvertingFluidHandler(IFluidHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public int getTanks() { 
        return delegate.getTanks(); 
    }

    @Override
    public FluidStack getFluidInTank(int tank) { 
        FluidStack stack = delegate.getFluidInTank(tank);
        if (!stack.isEmpty() && stack.getFluid() == SulfurFluids.HYDROTREATED_DIESEL_ENTRY.source.get()) {
            return new FluidStack(ModFluids.DIESEL_SOURCE.get(), stack.getAmount());
        }
        return stack; 
    }

    @Override
    public int getTankCapacity(int tank) { 
        return delegate.getTankCapacity(tank); 
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) { 
        return delegate.isFluidValid(tank, stack); 
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        // Intercept and convert Hydrotreated Diesel into Diesel upon entering the pipe
        if (resource.getFluid() == SulfurFluids.HYDROTREATED_DIESEL_ENTRY.source.get()) {
            FluidStack converted = new FluidStack(ModFluids.DIESEL_SOURCE.get(), resource.getAmount());
            return delegate.fill(converted, action);
        }
        return delegate.fill(resource, action);
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.getFluid() == ModFluids.DIESEL_SOURCE.get()) {
            FluidStack drained = delegate.drain(resource, action);
            if (!drained.isEmpty()) return drained;

            FluidStack hydroRequest = new FluidStack(SulfurFluids.HYDROTREATED_DIESEL_ENTRY.source.get(), resource.getAmount());
            FluidStack hydroDrained = delegate.drain(hydroRequest, action);
            if (!hydroDrained.isEmpty()) {
                return new FluidStack(ModFluids.DIESEL_SOURCE.get(), hydroDrained.getAmount());
            }
            return FluidStack.EMPTY;
        }

        if (resource.getFluid() == SulfurFluids.HYDROTREATED_DIESEL_ENTRY.source.get()) {
            FluidStack drained = delegate.drain(resource, action);
            if (!drained.isEmpty()) {
                return new FluidStack(ModFluids.DIESEL_SOURCE.get(), drained.getAmount());
            }
            return FluidStack.EMPTY;
        }

        return delegate.drain(resource, action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        FluidStack current = delegate.getFluidInTank(0);
        FluidStack drained = delegate.drain(maxDrain, action);
        if (!drained.isEmpty() && current.getFluid() == SulfurFluids.HYDROTREATED_DIESEL_ENTRY.source.get()) {
            return new FluidStack(ModFluids.DIESEL_SOURCE.get(), drained.getAmount());
        }
        return drained;
    }
}
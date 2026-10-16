package de.nils.lmswap.core;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * A {@link Scope} converted to region coordinates of one schematic: per region either everything, nothing, or a list
 * of local boxes. Placement rotation and mirroring only use 90° steps, so axis aligned world boxes stay axis aligned.
 */
public final class ResolvedScope
{
    public static final ResolvedScope ALL = new ResolvedScope(null);

    /** null = every region completely; otherwise regions missing from the map are excluded */
    @Nullable private final Map<String, RegionPart> regions;

    private ResolvedScope(@Nullable Map<String, RegionPart> regions)
    {
        this.regions = regions;
    }

    public static ResolvedScope of(Map<String, RegionPart> regions)
    {
        return new ResolvedScope(Map.copyOf(regions));
    }

    public boolean isAll()
    {
        return this.regions == null;
    }

    /** True if no region is affected at all */
    boolean isEmptyMap()
    {
        return this.regions != null && this.regions.isEmpty();
    }

    /**
     * @return null if the region is not affected at all
     */
    @Nullable
    public RegionPart forRegion(String regionName)
    {
        return this.regions == null ? RegionPart.WHOLE : this.regions.get(regionName);
    }

    /**
     * The affected part of one region: the whole region, or the union of inclusive local boxes.
     */
    public record RegionPart(@Nullable List<LocalBox> boxes)
    {
        public static final RegionPart WHOLE = new RegionPart(null);

        public boolean isWhole()
        {
            return this.boxes == null;
        }

        public boolean contains(int x, int y, int z)
        {
            if (this.boxes == null)
            {
                return true;
            }

            for (LocalBox box : this.boxes)
            {
                if (box.contains(x, y, z))
                {
                    return true;
                }
            }

            return false;
        }
    }

    /**
     * Inclusive box in container coordinates of one region.
     */
    public record LocalBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ)
    {
        public boolean contains(int x, int y, int z)
        {
            return x >= this.minX && x <= this.maxX && y >= this.minY && y <= this.maxY && z >= this.minZ && z <= this.maxZ;
        }
    }
}

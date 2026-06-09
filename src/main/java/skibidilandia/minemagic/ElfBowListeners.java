package skibidilandia.minemagic;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Comportamento do Arco Élfico.
 *
 * Ao atirar, além da flecha central (a do próprio disparo do vanilla), o arco
 * cospe mais duas flechas: uma desviada para a esquerda e outra para a direita,
 * abrindo um leque de 3 flechas como no Archero.
 *
 * Carrega {@link #CHARGE_MULTIPLIER}x mais rápido que um arco normal: a força do
 * disparo é remapeada como se o arco tivesse sido puxado por esse tanto a mais de
 * tempo, então soltar com metade do puxão de um arco normal já dá plena potência.
 *
 * Só a flecha do meio consome munição/durabilidade — as laterais são mágicas e
 * não podem ser recolhidas (evita duplicar flechas no chão).
 */
public class ElfBowListeners implements Listener {

    /** Desvio (graus) de cada flecha lateral em relação à do centro. */
    private static final double SPREAD_DEGREES = 10.0D;
    /** Quantas vezes mais rápido o arco carrega em relação a um arco normal. */
    private static final double CHARGE_MULTIPLIER = 2.0D;

    private final JavaPlugin plugin;

    public ElfBowListeners(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        if (!MineMagicItems.isElfBow(event.getBow())) {
            return;
        }
        if (!(event.getProjectile() instanceof AbstractArrow)) {
            return; // p.ex. bola de neve não dispara o leque
        }

        Player player = (Player) event.getEntity();
        AbstractArrow center = (AbstractArrow) event.getProjectile();
        World world = player.getWorld();
        Location spawn = center.getLocation();

        // Carrega 3x mais rápido: trata a força como se o arco tivesse sido
        // puxado por CHARGE_MULTIPLIER vezes mais tempo e reescala a flecha
        // central para essa potência (na velocidade do arco está embutida a força).
        float force = event.getForce();
        float boosted = boostForce(force, CHARGE_MULTIPLIER);
        if (force > 0.0f) {
            center.setVelocity(center.getVelocity().multiply(boosted / force));
        }
        center.setCritical(boosted >= 1.0f);
        Vector velocity = center.getVelocity();

        // Uma flecha desviada para cada lado, mantendo a velocidade da central.
        double angle = Math.toRadians(SPREAD_DEGREES);
        spawnSide(world, spawn, player, center, rotateY(velocity, angle));
        spawnSide(world, spawn, player, center, rotateY(velocity, -angle));

        world.playSound(spawn, Sound.ENTITY_ARROW_SHOOT, 0.6f, 1.3f);
    }

    /**
     * Remapeia a força de disparo (0..1) como se o arco tivesse sido puxado por
     * {@code multiplier} vezes mais tempo. A força do arco do Minecraft segue
     * {@code f = (t² + 2t) / 3}, onde {@code t} é a fração do puxão (0..1); aqui
     * invertemos para achar {@code t}, multiplicamos o tempo e reaplicamos a curva.
     */
    private float boostForce(float force, double multiplier) {
        if (force <= 0.0f) {
            return force;
        }
        if (force >= 1.0f) {
            return 1.0f;
        }
        double t = -1.0D + Math.sqrt(1.0D + 3.0D * force);
        double boostedT = Math.min(1.0D, t * multiplier);
        double boostedForce = (boostedT * boostedT + 2.0D * boostedT) / 3.0D;
        return (float) Math.min(1.0D, boostedForce);
    }

    /** Cria uma flecha lateral espelhando as propriedades da flecha central. */
    private void spawnSide(World world, Location spawn, Player shooter, AbstractArrow center, Vector velocity) {
        Arrow arrow = world.spawnArrow(spawn, velocity, (float) velocity.length(), 0.0f);
        arrow.setVelocity(velocity); // preserva exatamente a magnitude/direção
        arrow.setShooter(shooter);
        arrow.setCritical(center.isCritical());
        arrow.setDamage(center.getDamage());
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
    }

    /** Gira o vetor no plano horizontal (em torno do eixo Y) pelo ângulo dado. */
    private Vector rotateY(Vector v, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double x = v.getX() * cos - v.getZ() * sin;
        double z = v.getX() * sin + v.getZ() * cos;
        return new Vector(x, v.getY(), z);
    }

    /** Mantido por simetria com os outros listeners; o arco não tem tarefas. */
    public void shutdown() {
    }
}

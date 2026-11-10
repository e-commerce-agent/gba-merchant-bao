<script setup lang="ts">
import PublicHeader from '@/components/PublicHeader.vue'
import PublicFooter from '@/components/PublicFooter.vue'
</script>

<template>
  <div class="public-layout">
    <PublicHeader />
    <main class="public-layout__main">
      <RouterView v-slot="{ Component }">
        <Transition name="page" mode="out-in">
          <component :is="Component" />
        </Transition>
      </RouterView>
    </main>
    <PublicFooter />
  </div>
</template>

<style scoped lang="scss">
.public-layout {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  background: var(--bg-page);
}

.public-layout__main {
  flex: 1;
}
</style>

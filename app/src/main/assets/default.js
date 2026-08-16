'use strict';

rpc.exports = {
  init(stage, parameters) {
    console.log('[GadgetLoader] Frida script initialized');
    console.log('[GadgetLoader] stage=' + stage + ' pid=' + Process.id + ' arch=' + Process.arch);

    const interesting = new Set(['libunity.so', 'libil2cpp.so', 'libmonobdwgc-2.0.so']);

    function report(name) {
      const module = Process.findModuleByName(name);
      if (module !== null) {
        console.log('[GadgetLoader] ' + module.name + ' @ ' + module.base + ' size=' + module.size);
      }
    }

    interesting.forEach(report);

    Process.attachModuleObserver({
      onAdded(module) {
        if (interesting.has(module.name)) {
          console.log('[GadgetLoader] module loaded: ' + module.name + ' @ ' + module.base);
        }
      }
    });
  },

  dispose() {
    console.log('[GadgetLoader] script disposed');
  }
};
